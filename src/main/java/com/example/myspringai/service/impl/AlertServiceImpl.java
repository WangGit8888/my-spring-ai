package com.example.myspringai.service.impl;

import com.example.myspringai.config.RabbitMQConfig;
import com.example.myspringai.domain.AlarmEvent;
import com.example.myspringai.domain.HikvisionAlarmRequest;
import com.example.myspringai.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 预警处理服务实现：Redis 幂等 + MQ 发送
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private static final String REDIS_KEY_PREFIX = "alarm:processed:";
    private static final Duration REDIS_TTL = Duration.ofHours(24);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RedisTemplate<String, String> redisTemplate;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public boolean handleAlarm(HikvisionAlarmRequest request) {
        String alarmId = request.getAlarmId();

        // 1. Redis 幂等校验（原子操作 SETNX）
        String redisKey = REDIS_KEY_PREFIX + alarmId;
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", REDIS_TTL);

        if (Boolean.FALSE.equals(isNew)) {
            log.info("重复预警，跳过: alarmId={}", alarmId);
            return false;
        }

        // 2. 构建事件并发送到 MQ
        AlarmEvent event = buildEvent(request);
        sendToMq(event);

        log.info("预警已接收并发送到MQ: alarmId={}, type={}", alarmId, request.getAlarmType());
        return true;
    }

    @Override
    public void sendToMq(AlarmEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_ALARM,
                RabbitMQConfig.ROUTING_KEY,
                event
        );
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
