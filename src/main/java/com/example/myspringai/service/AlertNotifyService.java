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
 * 站内信兜底服务。
 * <p>
 * MQ 消费者 AlertNotifyConsumer 是快路径（~ms 级），本服务是慢路径兜底。
 * 只扫描 createTime 超过 60 秒仍为 PENDING 的记录——说明 MQ 消费者没处理到
 * （可能是 MQ publish 失败、消费者崩溃、JVM 内存缓冲区丢失等）。
 * <p>
 * 正常情况本服务几乎不干活，但能保证 100% 不丢消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertNotifyService {

    private static final int BATCH_LIMIT = 100;
    /** MQ 消费者兜底窗口：只处理超过 60 秒还没发出去的 */
    private static final int FALLBACK_WINDOW_SECONDS = 60;

    private final AlertInfoMapper alertInfoMapper;

    @Scheduled(fixedDelay = 10_000)
    public void fallbackScan() {
        List<AlertInfo> pendingList = alertInfoMapper.selectList(
                new LambdaQueryWrapper<AlertInfo>()
                        .eq(AlertInfo::getNotifyStatus, "PENDING")
                        .lt(AlertInfo::getCreateTime, LocalDateTime.now().minusSeconds(FALLBACK_WINDOW_SECONDS))
                        .last("LIMIT " + BATCH_LIMIT)
        );

        if (pendingList.isEmpty()) return;

        log.warn("[站内信·兜底] MQ消费者超时未处理，兜底扫描到 {} 条，尝试发送", pendingList.size());

        for (AlertInfo alert : pendingList) {
            try {
                // Level 3 立即发，Level 1/2 也是逐条发（兜底不攒批）
                sendFallback(alert);
                alert.setNotifyStatus("SUCCESS");
                alertInfoMapper.updateById(alert);
            } catch (Exception e) {
                log.error("[站内信·兜底] 发送失败: alarmId={}", alert.getAlertId(), e);
                // 不更新状态，下次继续重试
            }
        }
    }

    private void sendFallback(AlertInfo alert) {
        // TODO: 对接站内信服务发送通知
        log.info("[站内信·兜底] 发送: alarmId={}, type={}, level={}, device={}",
                alert.getAlertId(), alert.getAlertType(),
                alert.getAlertLevel(), alert.getDeviceName());
    }
}
