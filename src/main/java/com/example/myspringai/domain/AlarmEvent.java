package com.example.myspringai.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * RabbitMQ 消息体 — 海康预警事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmEvent implements Serializable {

    /** 海康预警唯一ID */
    private String alarmId;

    /** 预警类型 */
    private String alarmType;

    /** 预警内容 */
    private String alarmContent;

    /** 预警触发时间 */
    private LocalDateTime alarmTime;

    /** 设备ID */
    private String deviceId;

    /** 设备名称 */
    private String deviceName;

    /** 海康推送的原始数据 */
    private Map<String, Object> rawData;

    /** 系统接收时间 */
    private LocalDateTime receiveTime;
}
