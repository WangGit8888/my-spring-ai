package com.example.myspringai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.myspringai.domain.AlertInfo;
import com.example.myspringai.domain.HikvisionAlarmRequest;
import com.example.myspringai.mapper.AlertInfoMapper;
import com.example.myspringai.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 海康 ISC 预警接收接口
 */
@Slf4j
@RestController
@RequestMapping("/api/hikvision")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final AlertInfoMapper alertInfoMapper;

    /**
     * 接收海康 ISC 平台推送的预警。
     * 直接落库（唯一索引幂等），通知/短信由定时任务异步发送，响应时间 ~5ms。
     */
    @PostMapping("/alarm")
    public ResponseEntity<?> receiveAlarm(@RequestBody HikvisionAlarmRequest request) {
        if (request.getAlarmId() == null || request.getAlarmId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "alarmId 不能为空"
            ));
        }

        try {
            boolean isNew = alertService.handleAlarm(request);
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", isNew ? "success" : "duplicate",
                    "data", Map.of("alarmId", request.getAlarmId(), "new", isNew)
            ));
        } catch (Exception e) {
            log.error("处理海康预警失败: alarmId={}", request.getAlarmId(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "处理失败: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/getAlertById")
    public void getAlertById(@RequestParam("alarmId") Long alarmId) {
        AlertInfo alertInfo = alertInfoMapper.selectOne(
                new LambdaQueryWrapper<AlertInfo>().eq(AlertInfo::getAlertId, alarmId)
        );
        System.out.println(alertInfo);
    }
}
