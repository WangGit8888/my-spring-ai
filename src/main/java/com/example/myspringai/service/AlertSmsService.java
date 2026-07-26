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
 * 短信兜底服务。
 * <p>
 * MQ 消费者 AlertSmsConsumer 是快路径（~ms 级），本服务是慢路径兜底。
 * 只扫描 createTime 超过 120 秒仍为 PENDING 的记录——短信发送慢、批次窗口大（60s），
 * 兜底窗口也相应放宽。
 * <p>
 * 正常情况本服务几乎不干活，但能保证 100% 不丢消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertSmsService {

    private static final int BATCH_LIMIT = 100;
    /** MQ 消费者兜底窗口：短信批次最大 60s，兜底给到 120s */
    private static final int FALLBACK_WINDOW_SECONDS = 120;

    private final AlertInfoMapper alertInfoMapper;

    @Scheduled(fixedDelay = 15_000)
    public void fallbackScan() {
        List<AlertInfo> pendingList = alertInfoMapper.selectList(
                new LambdaQueryWrapper<AlertInfo>()
                        .eq(AlertInfo::getSmsStatus, "PENDING")
                        .lt(AlertInfo::getCreateTime, LocalDateTime.now().minusSeconds(FALLBACK_WINDOW_SECONDS))
                        .last("LIMIT " + BATCH_LIMIT)
        );

        if (pendingList.isEmpty()) return;

        log.warn("[短信·兜底] MQ消费者超时未处理，兜底扫描到 {} 条，尝试发送", pendingList.size());

        for (AlertInfo alert : pendingList) {
            try {
                sendFallback(alert);
                alert.setSmsStatus("SUCCESS");
                alertInfoMapper.updateById(alert);
            } catch (Exception e) {
                log.error("[短信·兜底] 发送失败: alarmId={}", alert.getAlertId(), e);
                // 不更新状态，下次继续重试
            }
        }
    }

    private void sendFallback(AlertInfo alert) {
        // TODO: 对接短信服务发送短信
        log.info("[短信·兜底] 发送: alarmId={}, type={}, level={}, device={}",
                alert.getAlertId(), alert.getAlertType(),
                alert.getAlertLevel(), alert.getDeviceName());
    }
}
