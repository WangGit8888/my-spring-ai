package com.example.myspringai.service.impl;

import com.example.myspringai.config.RabbitMQConfig;
import com.example.myspringai.domain.AlarmEvent;
import com.example.myspringai.domain.AlertInfo;
import com.example.myspringai.domain.HikvisionAlarmRequest;
import com.example.myspringai.mapper.AlertInfoMapper;
import com.example.myspringai.service.AlertService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 预警处理服务实现。
 * <p>
 * 入口：直接落库（alert_id 唯一索引幂等）→ 响应 ~5ms<br>
 * 出口：落库成功后 publish 到 MQ（快路径），通知和短信各自独立消费<br>
 * 兜底：定时任务 AlertNotifyService / AlertSmsService 扫描超时未发送的记录
 * <p>
 * MQ publish 失败不抛异常——DB 已落库 PENDING，定时任务会兜底，消息不会丢。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlertInfoMapper alertInfoMapper;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public boolean handleAlarm(HikvisionAlarmRequest request) {
        String alarmId = request.getAlarmId();

        // 1. 直接落库（唯一索引幂等）
        AlertInfo info = buildAlertInfo(request);
        try {
            alertInfoMapper.insert(info);
        } catch (DuplicateKeyException e) {
            log.info("重复预警，已跳过: alarmId={}", alarmId);
            return false;
        }

        // 2. 落库成功 → 发 MQ（快路径，通知和短信各自消费）
        //    失败不抛异常，定时任务兜底扫描 PENDING 记录
        AlarmEvent event = buildEvent(request);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_ALARM,
                    RabbitMQConfig.ROUTING_KEY,
                    event
            );
            log.info("预警已入库并投递MQ: alarmId={}, level={}", alarmId, info.getAlertLevel());
        } catch (Exception e) {
            log.error("MQ投递失败(定时任务将兜底): alarmId={}", alarmId, e);
            // 不抛异常，DB 里 notify_status/sms_status 都是 PENDING，定时任务会处理
        }

        return true;
    }

    private AlertInfo buildAlertInfo(HikvisionAlarmRequest req) {
        LocalDateTime alarmTime = null;
        try {
            if (req.getAlarmTime() != null) {
                alarmTime = LocalDateTime.parse(req.getAlarmTime(), DT_FMT);
            }
        } catch (Exception e) {
            log.warn("解析预警时间失败: {}", req.getAlarmTime());
        }

        return AlertInfo.builder()
                .alertId(req.getAlarmId())
                .alertType(req.getAlarmType())
                .alertContent(req.getAlarmContent())
                .alertTime(alarmTime)
                .deviceId(req.getDeviceId())
                .deviceName(req.getDeviceName())
                .alertLevel(req.getAlarmLevel() != null ? req.getAlarmLevel() : 1)
                .rawData(toJson(req.getRawData()))
                .receiveTime(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .notifyStatus("PENDING")
                .smsStatus("PENDING")
                .build();
    }

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

    private Map<String, Object> mergeRawData(HikvisionAlarmRequest req) {
        Map<String, Object> raw = req.getRawData() != null
                ? new HashMap<>(req.getRawData()) : new HashMap<>();
        raw.putIfAbsent("alarmId", req.getAlarmId());
        raw.putIfAbsent("alarmType", req.getAlarmType());
        raw.putIfAbsent("deviceId", req.getDeviceId());
        return raw;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
