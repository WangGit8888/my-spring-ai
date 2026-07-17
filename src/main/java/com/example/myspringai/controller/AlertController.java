package com.example.myspringai.controller;

import com.example.myspringai.domain.HikvisionAlarmRequest;
import com.example.myspringai.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * 接收海康 ISC 平台推送的预警。
     * 幂等校验后发 MQ，立即返回 200，响应时间控制在 120ms 左右。
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
}
