package com.example.myspringai.consumer;

import com.example.myspringai.config.RabbitMQConfig;
import com.example.myspringai.domain.AlarmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 站内信通知消费者
 * <p>
 * 当前为 Mock 实现，实际对接站内信服务时替换 TODO 部分。
 */
@Slf4j
@Component
public class AlertNotifyConsumer {

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFY)
    public void handleNotify(AlarmEvent event) {
        try {
            // TODO: 对接站内信服务发送通知
            log.info("[站内信] 发送预警通知: alarmId={}, type={}, device={}",
                    event.getAlarmId(), event.getAlarmType(), event.getDeviceName());
        } catch (Exception e) {
            log.error("[站内信] 发送失败: alarmId={}", event.getAlarmId(), e);
            // 抛出异常让 MQ 自动重试
            throw e;
        }
    }
}
