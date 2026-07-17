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
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 预警落库消费者 — 手动 ACK + 重试（间隔2s共3次）→ DLQ
 * <p>
 * 手动 ACK 确保只有真正落库成功才确认；
 * 重试拦截器处理瞬时故障（DB 连接抖动等）；
 * 重试耗尽后进入 alarm.db.dlq 死信队列。
 */

/**
 * note:消费者默认是单线程的;多线程并发消费不会出现同一消息被两个线程同时拿到。RabbitMQ 通过消息独占分发机制保证了这一点。
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertDbConsumer {

    private final AlertInfoMapper alertInfoMapper;
    private final ObjectMapper objectMapper;

    @RabbitListener(
            queues = RabbitMQConfig.QUEUE_DB,
            containerFactory = "dbListenerContainerFactory"
    )
    public void handleDb(AlarmEvent event,
                         Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {

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
            channel.basicAck(tag, false);

        } catch (DuplicateKeyException e) {
            // 唯一键冲突 → 已有相同预警入库 → 直接 ACK
            log.info("重复预警入库(幂等兜底): alertId={}", event.getAlarmId());
            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("预警落库失败: alarmId={}", event.getAlarmId(), e);
            // 抛出异常 → 重试拦截器接管 → 3次重试 → 仍失败则 DLQ
            throw e;
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
