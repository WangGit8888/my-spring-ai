package com.example.myspringai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.myspringai.domain.AlertInfo;
import com.example.myspringai.mapper.AlertInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 短信通知服务 — 定时轮询 DB 发送：
 * <p>
 * Level 3（严重）→ 立即发送<br>
 * Level 1/2（轻微/中等）→ 攒到 60 秒后批量发送（短信成本高，攒久一点）
 * <p>
 * 发送失败不更新状态，下次轮询自动重试。没有任何消息丢失风险。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertSmsService {

    private static final int BATCH_LIMIT = 100;
    private static final int BATCH_WINDOW_SECONDS = 60;

    private final AlertInfoMapper alertInfoMapper;

    /**
     * 每 3 秒扫描一次待发送的短信
     */
    @Scheduled(fixedDelay = 3000)
    public void processPendingSms() {

        // 1. Level 3（严重）：立即发送
        List<AlertInfo> urgentList = alertInfoMapper.selectList(
                new LambdaQueryWrapper<AlertInfo>()
                        .eq(AlertInfo::getSmsStatus, "PENDING")
                        .ge(AlertInfo::getAlertLevel, 3)
                        .last("LIMIT " + BATCH_LIMIT)
        );
        for (AlertInfo alert : urgentList) {
            try {
                sendImmediately(alert);
                alert.setSmsStatus("SUCCESS");
                alertInfoMapper.updateById(alert);
            } catch (Exception e) {
                log.error("[短信·紧急] 发送失败，等待下次重试: alarmId={}", alert.getAlertId(), e);
                // 不更新状态 → 下次轮询自动重试
            }
        }

        // 2. Level 1/2（轻微/中等）：超过 60 秒的批量发送
        List<AlertInfo> batchList = alertInfoMapper.selectList(
                new LambdaQueryWrapper<AlertInfo>()
                        .eq(AlertInfo::getSmsStatus, "PENDING")
                        .lt(AlertInfo::getAlertLevel, 3)
                        .lt(AlertInfo::getCreateTime, LocalDateTime.now().minusSeconds(BATCH_WINDOW_SECONDS))
                        .last("LIMIT " + BATCH_LIMIT)
        );
        if (!batchList.isEmpty()) {
            try {
                sendBatch(batchList);
                for (AlertInfo alert : batchList) {
                    alert.setSmsStatus("SUCCESS");
                    alertInfoMapper.updateById(alert);
                }
            } catch (Exception e) {
                log.error("[短信·批量] 发送失败，等待下次重试: count={}", batchList.size(), e);
                // 不更新状态 → 下次轮询自动重试
            }
        }
    }

    private void sendImmediately(AlertInfo alert) {
        // TODO: 对接短信服务发送短信
        log.info("[短信·紧急] 立即发送: alarmId={}, type={}, level={}, device={}",
                alert.getAlertId(), alert.getAlertType(),
                alert.getAlertLevel(), alert.getDeviceName());
    }

    private void sendBatch(List<AlertInfo> batch) {
        // TODO: 对接短信批量发送接口
        log.info("[短信·批量] 合并发送 {} 条预警: {}",
                batch.size(),
                batch.stream().map(AlertInfo::getAlertId).toList());
    }
}
