package com.example.myspringai.domain;

import lombok.Data;

import java.util.Map;

/**
 * 海康 ISC 平台推送的预警请求体
 */
@Data
public class HikvisionAlarmRequest {

    /** 预警唯一ID */
    private String alarmId;

    /** 预警类型 */
    private String alarmType;

    /** 预警内容 */
    private String alarmContent;

    /** 预警触发时间 */
    private String alarmTime;

    /** 设备ID */
    private String deviceId;

    /** 设备名称 */
    private String deviceName;

    /** 接收海康推送的全部原始字段（兜底） */
    private Map<String, Object> rawData;
}
