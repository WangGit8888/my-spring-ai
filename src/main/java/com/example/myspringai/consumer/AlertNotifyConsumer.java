package com.example.myspringai.consumer;

import com.example.myspringai.config.RabbitMQConfig;
import com.example.myspringai.domain.AlarmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 站内信通知消费者 — 使用 notifyListenerContainerFactory，失败自动重试 3 次（间隔 5s）
 * <p>
 * 重试 3 次全部失败后 → 进入死信队列 alarm.notify.dlq，由运维人工兜底。
 * <p>
 * 当前为 Mock 实现，实际对接站内信服务时替换 TODO 部分。
 */
@Slf4j
@Component
public class AlertNotifyConsumer {

    @RabbitListener(
            queues = RabbitMQConfig.QUEUE_NOTIFY,
            containerFactory = "notifyListenerContainerFactory"
    )
    public void handleNotify(AlarmEvent event) {
        // TODO: 对接站内信服务发送通知
        log.info("[站内信] 发送预警通知: alarmId={}, type={}, device={}",
                event.getAlarmId(), event.getAlarmType(), event.getDeviceName());

        // 模拟站内信接口偶发失败（正式上线后删除）
        // if (Math.random() < 0.5) {
        //     throw new RuntimeException("模拟站内信发送失败");
        // }
    }
}
