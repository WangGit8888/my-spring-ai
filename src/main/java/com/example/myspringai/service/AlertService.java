package com.example.myspringai.service;

import com.example.myspringai.domain.AlarmEvent;
import com.example.myspringai.domain.HikvisionAlarmRequest;

/**
 * 预警处理服务
 */
public interface AlertService {

    /**
     * 处理海康预警：幂等校验 → 发 MQ → 返回
     *
     * @param request 海康推送的预警请求
     * @return true-新预警已处理, false-重复预警已跳过
     */
    boolean handleAlarm(HikvisionAlarmRequest request);


}
