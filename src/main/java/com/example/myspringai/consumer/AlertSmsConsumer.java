package com.example.myspringai.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.myspringai.config.RabbitMQConfig;
import com.example.myspringai.domain.AlarmEvent;
import com.example.myspringai.domain.AlertInfo;
import com.example.myspringai.mapper.AlertInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 短信通知消费者（MQ 快路径）：
 * <p>
 * Level 3（严重）→ 立即发送并更新 DB 状态<br>
 * Level 1/2（轻微/中等）→ 内存攒批（5条或60秒）后发送
 * <p>
 * 注意：发送成功后更新 alert_info.sms_status = SUCCESS，
 * 这样兜底定时任务 AlertSmsService 不会再重复发送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertSmsConsumer {

    private static final int BATCH_MAX_SIZE = 5;

    private final AlertInfoMapper alertInfoMapper;
    private final List<AlarmEvent> batchBuffer = new ArrayList<>();
    private volatile long lastFlushTime = System.currentTimeMillis();

    @RabbitListener(
            queues = RabbitMQConfig.QUEUE_SMS,
            containerFactory = "smsListenerContainerFactory"
    )
    public void handleSms(AlarmEvent event) throws Exception {
        int level = event.getAlarmLevel() != null ? event.getAlarmLevel() : 1;

        if (level >= 3) {
            sendImmediately(event);
            markSmsSuccess(event.getAlarmId());
        } else {
            synchronized (batchBuffer) {
                batchBuffer.add(event);
                if (batchBuffer.size() >= BATCH_MAX_SIZE) {
                    flushBatch();
                }
            }
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void scheduledFlush() {
        synchronized (batchBuffer) {
            long elapsed = System.currentTimeMillis() - lastFlushTime;
            if (!batchBuffer.isEmpty() && elapsed >= 60_000) {
                flushBatch();
            }
        }
    }

    private void flushBatch() {
        if (batchBuffer.isEmpty()) return;
        List<AlarmEvent> batch = new ArrayList<>(batchBuffer);
        batchBuffer.clear();
        lastFlushTime = System.currentTimeMillis();

        // TODO: 实际对接短信批量发送接口
        log.info("[短信·批量] 合并发送 {} 条预警: {}",
                batch.size(),
                batch.stream().map(AlarmEvent::getAlarmId).toList());

        for (AlarmEvent e : batch) {
            markSmsSuccess(e.getAlarmId());
        }
    }

    private void sendImmediately(AlarmEvent event) {
        // TODO: 对接短信服务发送短信
        log.info("[短信·紧急] 立即发送: alarmId={}, type={}, level={}, device={}",
                event.getAlarmId(), event.getAlarmType(),
                event.getAlarmLevel(), event.getDeviceName());
    }

    private void markSmsSuccess(String alarmId) {
        try {
            AlertInfo info = alertInfoMapper.selectOne(
                    new LambdaQueryWrapper<AlertInfo>().eq(AlertInfo::getAlertId, alarmId));
            if (info != null) {
                info.setSmsStatus("SUCCESS");
                alertInfoMapper.updateById(info);
            }
        } catch (Exception e) {
            log.error("更新短信状态失败: alarmId={}", alarmId, e);
        }
    }
}
