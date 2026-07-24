package com.example.myspringai.service;

import com.example.myspringai.domain.HikvisionAlarmRequest;

/**
 * 预警处理服务
 */
public interface AlertService {

    /**
     * 处理海康预警：直接落库（alert_id 唯一索引幂等），异步定时任务兜底发送通知/短信
     *
     * @param request 海康推送的预警请求
     * @return true-新预警已入库, false-重复预警已跳过
     */
    boolean handleAlarm(HikvisionAlarmRequest request);
}
