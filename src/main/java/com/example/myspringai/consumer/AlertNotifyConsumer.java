package com.example.myspringai.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.myspringai.config.RabbitMQConfig;
import com.example.myspringai.domain.AlarmEvent;
import com.example.myspringai.domain.AlertInfo;
import com.example.myspringai.mapper.AlertInfoMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 站内信通知消费者（MQ 快路径，MANUAL ACK）。
 * <p>
 * Level 3（严重）→ 立即发送，成功 ACK，失败抛异常 → 重试 3 次 → DLQ<br>
 * Level 1/2（轻微/中等）→ 直接 ACK，DB 保持 PENDING，由 AlertNotifyService 定时批量发送
 * <p>
 * 不再使用内存缓冲区攒批——进程崩溃零丢失。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertNotifyConsumer {

    private final AlertInfoMapper alertInfoMapper;

    @RabbitListener(
            queues = RabbitMQConfig.QUEUE_NOTIFY,
            containerFactory = "notifyListenerContainerFactory"
    )
    public void handleNotify(AlarmEvent event,
                             Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        int level = event.getAlarmLevel() != null ? event.getAlarmLevel() : 1;

        if (level >= 3) {
            // Level 3：立即发送，成功才 ACK，失败抛异常让重试拦截器接管
            try {
                sendImmediately(event);
                channel.basicAck(tag, false); // 发送成功立刻ACK，避免MQ重试导致重复发送
                markNotifySuccess(event.getAlarmId());
                log.info("[站内信·紧急] 发送成功: alarmId={}", event.getAlarmId());
            } catch (Exception e) {
                log.error("[站内信·紧急] 发送失败，等待重试: alarmId={}", event.getAlarmId(), e);
                throw e; // 抛异常 → notifyRetryInterceptor 重试 3 次 → 耗尽进 DLQ
            }
        } else {
            // Level 1/2：直接 ACK，DB 里 notify_status=PENDING 由定时任务批量发送
            channel.basicAck(tag, false);
            log.debug("[站内信·普通] 已ACK，等待定时任务批量发送: alarmId={}", event.getAlarmId());
        }
    }

    private void sendImmediately(AlarmEvent event) {
        // TODO: 对接站内信服务发送通知
        log.info("[站内信·紧急] 立即发送: alarmId={}, type={}, level={}, device={}",
                event.getAlarmId(), event.getAlarmType(),
                event.getAlarmLevel(), event.getDeviceName());
    }

    private void markNotifySuccess(String alarmId) {
        try {
            AlertInfo info = alertInfoMapper.selectOne(
                    new LambdaQueryWrapper<AlertInfo>().eq(AlertInfo::getAlertId, alarmId));
            if (info != null) {
                info.setNotifyStatus("SUCCESS");
                alertInfoMapper.updateById(info);
            }
        } catch (Exception e) {
            log.error("更新站内信状态失败: alarmId={}", alarmId, e);
        }
    }
}
