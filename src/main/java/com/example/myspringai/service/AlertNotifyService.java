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
 * 站内信通知服务 — 定时轮询 DB 发送：
 * <p>
 * Level 3（严重）→ 立即发送<br>
 * Level 1/2（轻微/中等）→ 攒到 30 秒后批量发送
 * <p>
 * 发送失败不更新状态，下次轮询自动重试。没有任何消息丢失风险。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertNotifyService {

    private static final int BATCH_LIMIT = 100;
    private static final int BATCH_WINDOW_SECONDS = 30;

    private final AlertInfoMapper alertInfoMapper;

    /**
     * 每 3 秒扫描一次待发送的站内信
     */
    @Scheduled(fixedDelay = 3000)
    public void processPendingNotifications() {

        // 1. Level 3（严重）：立即发送
        List<AlertInfo> urgentList = alertInfoMapper.selectList(
                new LambdaQueryWrapper<AlertInfo>()
                        .eq(AlertInfo::getNotifyStatus, "PENDING")
                        .ge(AlertInfo::getAlertLevel, 3)
                        .last("LIMIT " + BATCH_LIMIT)
        );
        for (AlertInfo alert : urgentList) {
            try {
                sendImmediately(alert);
                alert.setNotifyStatus("SUCCESS");
                alertInfoMapper.updateById(alert);
            } catch (Exception e) {
                log.error("[站内信·紧急] 发送失败，等待下次重试: alarmId={}", alert.getAlertId(), e);
                // 不更新状态 → 下次轮询自动重试
            }
        }

        // 2. Level 1/2（轻微/中等）：超过 30 秒的批量发送
        List<AlertInfo> batchList = alertInfoMapper.selectList(
                new LambdaQueryWrapper<AlertInfo>()
                        .eq(AlertInfo::getNotifyStatus, "PENDING")
                        .lt(AlertInfo::getAlertLevel, 3)
                        .lt(AlertInfo::getCreateTime, LocalDateTime.now().minusSeconds(BATCH_WINDOW_SECONDS))
                        .last("LIMIT " + BATCH_LIMIT)
        );
        if (!batchList.isEmpty()) {
            try {
                sendBatch(batchList);
                for (AlertInfo alert : batchList) {
                    alert.setNotifyStatus("SUCCESS");
                    alertInfoMapper.updateById(alert);
                }
            } catch (Exception e) {
                log.error("[站内信·批量] 发送失败，等待下次重试: count={}", batchList.size(), e);
                // 不更新状态 → 下次轮询自动重试
            }
        }
    }

    private void sendImmediately(AlertInfo alert) {
        // TODO: 对接站内信服务发送通知
        log.info("[站内信·紧急] 立即发送: alarmId={}, type={}, level={}, device={}",
                alert.getAlertId(), alert.getAlertType(),
                alert.getAlertLevel(), alert.getDeviceName());
    }

    private void sendBatch(List<AlertInfo> batch) {
        // TODO: 对接站内信批量发送接口
        log.info("[站内信·批量] 合并发送 {} 条预警: {}",
                batch.size(),
                batch.stream().map(AlertInfo::getAlertId).toList());
    }
}
