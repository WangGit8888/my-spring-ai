package com.example.myspringai.consumer;

import com.example.myspringai.config.RabbitMQConfig;
import com.example.myspringai.domain.AlarmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 站内信通知消费者 — 按预警等级分流：
 * <p>
 * Level 3（严重）→ 立即发送<br>
 * Level 1/2（轻微/中等）→ 攒批，满了10条或超过30秒发送
 */
@Slf4j
@Component
public class AlertNotifyConsumer {

    private static final int BATCH_MAX_SIZE = 10;

    private final List<AlarmEvent> batchBuffer = new ArrayList<>();
    private volatile long lastFlushTime = System.currentTimeMillis();

    @RabbitListener(
            queues = RabbitMQConfig.QUEUE_NOTIFY,
            containerFactory = "notifyListenerContainerFactory"
    )
    public void handleNotify(AlarmEvent event) throws Exception {
        int level = event.getAlarmLevel() != null ? event.getAlarmLevel() : 1;

        if (level >= 3) {
            // 严重 → 立即发送
            sendImmediately(event);
        } else {
            // 轻微/中等 → 入缓冲池
            synchronized (batchBuffer) {
                batchBuffer.add(event);
                if (batchBuffer.size() >= BATCH_MAX_SIZE) {
                    flushBatch();
                }
            }
        }
    }

    /**
     * 定时兜底：每 30 秒检查一次，超过时间阈值就刷
     */
    @Scheduled(fixedDelay = 30_000)
    public void scheduledFlush() {
        synchronized (batchBuffer) {
            long elapsed = System.currentTimeMillis() - lastFlushTime;
            if (!batchBuffer.isEmpty() && elapsed >= 30_000) {
                flushBatch();
            }
        }
    }

    private void flushBatch() {
        if (batchBuffer.isEmpty()) {
            return;
        }
        List<AlarmEvent> batch = new ArrayList<>(batchBuffer);
        batchBuffer.clear();
        lastFlushTime = System.currentTimeMillis();

        // TODO: 实际对接站内信批量发送接口
        log.info("[站内信·批量] 合并发送 {} 条预警: {}", batch.size(),
                batch.stream().map(AlarmEvent::getAlarmId).toList());
    }

    private void sendImmediately(AlarmEvent event) {
        // TODO: 对接站内信服务发送通知
        log.info("[站内信·紧急] 立即发送: alarmId={}, type={}, level={}, device={}",
                event.getAlarmId(), event.getAlarmType(),
                event.getAlarmLevel(), event.getDeviceName());
    }
}
