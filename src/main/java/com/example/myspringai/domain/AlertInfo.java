package com.example.myspringai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.myspringai.jiami.CryptoTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 海康预警记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("alert_info")
public class AlertInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 海康预警唯一ID（唯一索引，幂等） */
    private String alertId;

    /** 预警类型（区域入侵/越界/徘徊等） */
    private String alertType;

    /** 预警内容描述 */
    private String alertContent;

    /** 预警触发时间 */
    private LocalDateTime alertTime;

    /** 设备ID */
    private String deviceId;

    /** 设备名称（自动加解密） */
    @TableField(typeHandler = CryptoTypeHandler.class)
    private String deviceName;

    /** 预警等级：1-轻微 2-中等 3-严重 */
    private Integer alertLevel;

    /** 原始推送数据JSON */
    private String rawData;

    /** 系统接收时间 */
    private LocalDateTime receiveTime;

    /** 记录创建时间 */
    private LocalDateTime createTime;

    /** 站内信发送状态：PENDING / SUCCESS / FAILED */
    private String notifyStatus;

    /** 短信发送状态：PENDING / SUCCESS / FAILED */
    private String smsStatus;
}
