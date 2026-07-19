package com.example.myspringai.consumer;

import com.example.myspringai.config.RabbitMQConfig;
import com.example.myspringai.domain.AlarmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 短信通知消费者 — AUTO ack + 重试拦截器（间隔5s共3次）→ DLQ
 * <p>
 * 抛异常 → 重试拦截器自动重试 → 耗尽后容器自动 reject → alarm.sms.dlq。
 */
@Slf4j
@Component
public class AlertSmsConsumer {

    @RabbitListener(
            queues = RabbitMQConfig.QUEUE_SMS,
            containerFactory = "smsListenerContainerFactory"
    )
    public void handleSms(AlarmEvent event) throws Exception {
        // TODO: 对接短信服务发送短信
        log.info("[短信] 发送短信通知: alarmId={}, content={}, device={}",
                event.getAlarmId(), event.getAlarmContent(), event.getDeviceName());

        // 抛异常 → 重试拦截器接管(3次×5s) → 耗尽 → 容器自动 reject → DLQ
        // if (Math.random() < 0.5) {
        //     throw new RuntimeException("模拟短信发送失败");
        // }
    }
}
