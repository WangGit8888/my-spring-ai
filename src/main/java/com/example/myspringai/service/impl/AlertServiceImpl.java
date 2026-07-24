package com.example.myspringai.service.impl;

import com.example.myspringai.domain.AlertInfo;
import com.example.myspringai.domain.HikvisionAlarmRequest;
import com.example.myspringai.mapper.AlertInfoMapper;
import com.example.myspringai.service.AlertService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 预警处理服务实现：直接落库，alert_id 唯一索引做幂等。
 * <p>
 * 落库成功后通知/短信状态为 PENDING，由 AlertNotifyService / AlertSmsService
 * 定时轮询发送，失败自动重试。链路极短，不会丢消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlertInfoMapper alertInfoMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean handleAlarm(HikvisionAlarmRequest request) {
        AlertInfo info = buildAlertInfo(request);
        try {
            alertInfoMapper.insert(info);
            log.info("预警入库成功: alarmId={}, level={}", request.getAlarmId(), info.getAlertLevel());
            return true;
        } catch (DuplicateKeyException e) {
            log.info("重复预警，已跳过: alarmId={}", request.getAlarmId());
            return false;
        }
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

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
