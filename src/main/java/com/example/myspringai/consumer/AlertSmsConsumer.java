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
 * 短信通知消费者 — 手动 ACK + 重试（间隔5s共3次）→ DLQ
 * <p>
 * 手动 ACK 确保短信真正发送成功才确认；
 * 重试耗尽后进入 alarm.sms.dlq。
 */
@Slf4j
@Component
public class AlertSmsConsumer {

    @RabbitListener(
            queues = RabbitMQConfig.QUEUE_SMS,
            containerFactory = "smsListenerContainerFactory"
    )
    public void handleSms(AlarmEvent event,
                           Channel channel,
                           @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {

        try {
            // TODO: 对接短信服务发送短信
            log.info("[短信] 发送短信通知: alarmId={}, content={}, device={}",
                    event.getAlarmId(), event.getAlarmContent(), event.getDeviceName());

            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("[短信] 发送失败: alarmId={}", event.getAlarmId(), e);
            throw e; // 重试拦截器接管 → DLQ
        }
    }
}
