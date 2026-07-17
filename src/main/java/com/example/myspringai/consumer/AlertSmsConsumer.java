package com.example.myspringai.consumer;

import com.example.myspringai.config.RabbitMQConfig;
import com.example.myspringai.domain.AlarmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 短信通知消费者 — 使用 smsListenerContainerFactory，失败自动重试 3 次（间隔 5s）
 * <p>
 * 重试 3 次全部失败后 → 进入死信队列 alarm.sms.dlq，由运维人工兜底。
 * <p>
 * 当前为 Mock 实现，实际对接短信服务时替换 TODO 部分。
 */
@Slf4j
@Component
public class AlertSmsConsumer {

    @RabbitListener(
            queues = RabbitMQConfig.QUEUE_SMS,
            containerFactory = "smsListenerContainerFactory"  // 使用带重试拦截器的工厂
    )
    public void handleSms(AlarmEvent event) {
        // TODO: 对接短信服务发送短信
        log.info("[短信] 发送短信通知: alarmId={}, content={}, device={}",
                event.getAlarmId(), event.getAlarmContent(), event.getDeviceName());

        // 模拟短信接口偶发失败（正式上线后删除）
        // if (Math.random() < 0.5) {
        //     throw new RuntimeException("模拟短信发送失败");
        // }
    }
}
