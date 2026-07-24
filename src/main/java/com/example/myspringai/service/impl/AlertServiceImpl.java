package com.example.myspringai.service.impl;

import com.example.myspringai.config.RabbitMQConfig;
import com.example.myspringai.domain.AlarmEvent;
import com.example.myspringai.domain.HikvisionAlarmRequest;
import com.example.myspringai.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 预警处理服务实现：Redis 幂等 + MQ 发送（带 publisher confirm）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private static final String REDIS_KEY_PREFIX = "alarm:processed:";
    private static final Duration REDIS_TTL = Duration.ofHours(24);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final RedisTemplate<String, String> redisTemplate;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public boolean handleAlarm(HikvisionAlarmRequest request) {
        String alarmId = request.getAlarmId();
        String redisKey = REDIS_KEY_PREFIX + alarmId;

        // 1. Redis 幂等校验（原子操作 SETNX）
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", REDIS_TTL);
        if (Boolean.FALSE.equals(isNew)) {
            log.info("重复预警，跳过: alarmId={}", alarmId);
            return false;
        }

        // 2. 构建事件并发送到 MQ（带 publisher confirm）
        AlarmEvent event = buildEvent(request);
        try {
            sendToMqWithConfirm(event);
            log.info("预警已接收并确认投递到MQ: alarmId={}, type={}", alarmId, request.getAlarmType());
            return true;
        } catch (Exception e) {
            // MQ 投递失败 → 回滚 Redis key，让海康重试时能再次进入
            redisTemplate.delete(redisKey);
            log.error("MQ投递失败，已回滚Redis: alarmId={}", alarmId, e);
            throw new RuntimeException("MQ投递失败，Redis已回滚，海康可重试", e);
        }
    }

    /**
     * 发送到 MQ 并等待 broker 确认。
     * <p>
     * 只发一次 convertAndSend，避免 confirm 超时时重复发送导致下游收到多条相同消息。
     * confirm 超时或 Nack → 删 Redis → 抛异常 → 海康重试。
     */
    private void sendToMqWithConfirm(AlarmEvent event) {
        CorrelationData correlationData = new CorrelationData();

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_ALARM,
                    RabbitMQConfig.ROUTING_KEY,
                    event,
                    correlationData
            );
        } catch (Exception e) {
            throw new RuntimeException("MQ发送失败(连接异常): " + e.getMessage(), e);
        }

        // 阻塞等待 broker 确认，超时 5 秒
        try {
            CorrelationData.Confirm confirm = correlationData
                    .getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!confirm.isAck()) {
                throw new RuntimeException("Broker Nack: "
                        + (confirm.getReason() != null ? confirm.getReason() : "未知原因"));
            }
            // ACK → 投递成功

        } catch (java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("等待MQ确认超时(" + CONFIRM_TIMEOUT_SECONDS + "s)"
                    + "，消息可能已发送，由海康重试兜底", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("等待MQ确认被中断", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RuntimeException("MQ确认异常: " + e.getCause().getMessage(), e.getCause());
        }
    }

    /**
     * 将海康请求转为 MQ 消息体
     */
    private AlarmEvent buildEvent(HikvisionAlarmRequest req) {
        LocalDateTime alarmTime = null;
        try {
            if (req.getAlarmTime() != null) {
                alarmTime = LocalDateTime.parse(req.getAlarmTime(), DT_FMT);
            }
        } catch (Exception e) {
            log.warn("解析预警时间失败: {}", req.getAlarmTime());
        }

        return AlarmEvent.builder()
                .alarmId(req.getAlarmId())
                .alarmType(req.getAlarmType())
                .alarmContent(req.getAlarmContent())
                .alarmTime(alarmTime)
                .deviceId(req.getDeviceId())
                .deviceName(req.getDeviceName())
                .alarmLevel(req.getAlarmLevel() != null ? req.getAlarmLevel() : 1)
                .rawData(mergeRawData(req))
                .receiveTime(LocalDateTime.now())
                .build();
    }

    /**
     * 合并请求中的已知字段到 rawData，保留完整海康原始数据
     */
    private Map<String, Object> mergeRawData(HikvisionAlarmRequest req) {
        Map<String, Object> raw = req.getRawData() != null
                ? new HashMap<>(req.getRawData()) : new HashMap<>();
        raw.putIfAbsent("alarmId", req.getAlarmId());
        raw.putIfAbsent("alarmType", req.getAlarmType());
        raw.putIfAbsent("deviceId", req.getDeviceId());
        return raw;
    }
}
