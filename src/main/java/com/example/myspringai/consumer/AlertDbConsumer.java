package com.example.myspringai.consumer;

import com.example.myspringai.config.RabbitMQConfig;
import com.example.myspringai.domain.AlarmEvent;
import com.example.myspringai.domain.AlertInfo;
import com.example.myspringai.mapper.AlertInfoMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 预警落库消费者 — 手动 ACK + 重试（间隔2s共3次）→ DLQ
 * <p>
 * 落库成功后发布 alarm.persisted 事件 → 下游站内信和短信消费者才开始处理，
 * 保证"落库失败 = 不发通知"。
 * <p>
 * note: 多线程并发消费不会出现同一消息被两个线程同时拿到。RabbitMQ 通过消息独占分发机制保证了这一点。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertDbConsumer {

    private final AlertInfoMapper alertInfoMapper;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(
            queues = RabbitMQConfig.QUEUE_DB,
            containerFactory = "dbListenerContainerFactory"
    )
    public void handleDb(AlarmEvent event,
                         Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {

        boolean shouldNotify = false;
        try {
            AlertInfo info = AlertInfo.builder()
                    .alertId(event.getAlarmId())
                    .alertType(event.getAlarmType())
                    .alertContent(event.getAlarmContent())
                    .alertTime(event.getAlarmTime())
                    .deviceId(event.getDeviceId())
                    .deviceName(event.getDeviceName())
                    .rawData(toJson(event.getRawData()))
                    .createTime(LocalDateTime.now())
                    .build();

            alertInfoMapper.insert(info);
            log.info("预警落库成功: alertId={}", event.getAlarmId());
            shouldNotify = true;
            channel.basicAck(tag, false);

        } catch (DuplicateKeyException e) {
            // 唯一键冲突 → 已有相同预警入库 → ACK，也可以发下游（数据已在库中）
            log.info("重复预警入库(幂等兜底): alertId={}", event.getAlarmId());
            shouldNotify = true;
            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("预警落库失败: alarmId={}", event.getAlarmId(), e);
            // 抛出异常 → 重试拦截器接管 → 3次重试 → 仍失败则 DLQ
            // 不会发下游事件，站内信和短信不会收到
            throw e;
        }

        // 落库成功 → 发布事件触发下游通知
        if (shouldNotify) {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE_PERSISTED,
                        RabbitMQConfig.ROUTING_KEY_PERSISTED,
                        event
                );
            } catch (Exception e) {
                // 发布失败只记日志，不影响落库这条消息的 ACK
                log.error("发布persisted事件失败(下游通知将丢失): alarmId={}", event.getAlarmId(), e);
            }
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
