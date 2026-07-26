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
 * 站内信批量发送服务 — DB 驱动攒批，无内存缓冲区。
 * <p>
 * MQ 消费者 AlertNotifyConsumer 只处理 Level 3 立即发送。<br>
 * Level 1/2 的批量发送全部由本服务负责：扫描 PENDING 超过 30s 的记录，合并发送。
 * <p>
 * 同时兜底 Level 3 超过 60s 仍 PENDING 的记录（MQ 消费者重试耗尽进 DLQ 的情况）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertNotifyService {

    private static final int BATCH_LIMIT = 200;
    private static final int BATCH_WINDOW_SECONDS = 30;
    private static final int FALLBACK_SECONDS = 60;

    private final AlertInfoMapper alertInfoMapper;

    /**
     * 每 5 秒扫描一次
     */
    @Scheduled(fixedDelay = 5_000)
    public void processPendingNotifications() {

        // 1. Level 1/2：攒到 30 秒后批量发送（主路径）
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
                log.info("[站内信·批量] 发送成功: {} 条", batchList.size());
            } catch (Exception e) {
                log.error("[站内信·批量] 发送失败，等待下次重试: count={}", batchList.size(), e);
                // 不更新状态，下次轮询自动重试
            }
        }

        // 2. Level 3 兜底：超过 60s 仍 PENDING（MQ 消费者处理失败进 DLQ 了）
        List<AlertInfo> fallbackList = alertInfoMapper.selectList(
                new LambdaQueryWrapper<AlertInfo>()
                        .eq(AlertInfo::getNotifyStatus, "PENDING")
                        .ge(AlertInfo::getAlertLevel, 3)
                        .lt(AlertInfo::getCreateTime, LocalDateTime.now().minusSeconds(FALLBACK_SECONDS))
                        .last("LIMIT " + BATCH_LIMIT)
        );
        for (AlertInfo alert : fallbackList) {
            try {
                sendImmediately(alert);
                alert.setNotifyStatus("SUCCESS");
                alertInfoMapper.updateById(alert);
                log.warn("[站内信·兜底] Level3超时未发，兜底发送成功: alarmId={}", alert.getAlertId());
            } catch (Exception e) {
                log.error("[站内信·兜底] 发送失败: alarmId={}", alert.getAlertId(), e);
            }
        }
    }

    private void sendImmediately(AlertInfo alert) {
        // TODO: 对接站内信服务发送通知
        log.info("[站内信·紧急] 发送: alarmId={}, type={}, level={}, device={}",
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
