package com.example.myspringai.consumer;

import com.example.myspringai.config.RabbitMQConfig;
import com.example.myspringai.domain.AlarmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 站内信通知消费者 — AUTO ack + 重试拦截器（间隔5s共3次）→ DLQ
 * <p>
 * 抛异常 → 重试拦截器自动重试 → 耗尽后容器自动 reject → alarm.notify.dlq。
 */
@Slf4j
@Component
public class AlertNotifyConsumer {

    @RabbitListener(
            queues = RabbitMQConfig.QUEUE_NOTIFY,
            containerFactory = "notifyListenerContainerFactory"
    )
    public void handleNotify(AlarmEvent event) throws Exception {
        // TODO: 对接站内信服务发送通知
        log.info("[站内信] 发送预警通知: alarmId={}, type={}, device={}",
                event.getAlarmId(), event.getAlarmType(), event.getDeviceName());

        // 抛异常 → 重试拦截器接管(3次×5s) → 耗尽 → 容器自动 reject → DLQ
        // int a = 1/0;
    }
}
