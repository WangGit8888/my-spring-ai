package com.example.myspringai.consumer;

import com.example.myspringai.config.RabbitMQConfig;
import com.example.myspringai.domain.AlarmEvent;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 站内信通知消费者 — 手动 ACK + 重试（间隔5s共3次）→ DLQ
 * <p>
 * 手动 ACK 确保站内信真正发送成功才确认；
 * 重试耗尽后进入 alarm.notify.dlq。
 */
@Slf4j
@Component
public class AlertNotifyConsumer {

    @RabbitListener(
            queues = RabbitMQConfig.QUEUE_NOTIFY,
            containerFactory = "notifyListenerContainerFactory"
    )
    public void handleNotify(AlarmEvent event,
                              Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {

        try {
            // TODO: 对接站内信服务发送通知
            log.info("[站内信] 发送预警通知: alarmId={}, type={}, device={}",
                    event.getAlarmId(), event.getAlarmType(), event.getDeviceName());

            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("[站内信] 发送失败: alarmId={}", event.getAlarmId(), e);
            throw e; // 重试拦截器接管 → DLQ
        }
    }
}
