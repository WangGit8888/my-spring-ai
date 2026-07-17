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
 * 预警落库消费者 — 手动 ACK，确保消息不丢
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertDbConsumer {

    private final AlertInfoMapper alertInfoMapper;
    private final ObjectMapper objectMapper;

    @RabbitListener(
            queues = RabbitMQConfig.QUEUE_DB,
            ackMode = "MANUAL"
    )
    public void handleDb(AlarmEvent event,
                         Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long tag) {

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

            // 手动确认
            channel.basicAck(tag, false);

        } catch (DuplicateKeyException e) {
            // 唯一键冲突说明已有相同预警入库，直接 ACK
            log.info("重复预警入库(幂等兜底): alertId={}", event.getAlarmId());
            ackQuietly(channel, tag);

        } catch (Exception e) {
            log.error("预警落库失败: alertId={}", event.getAlarmId(), e);
            // 其他异常 Nack 并重回队列
            nackQuietly(channel, tag);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private void ackQuietly(Channel channel, long tag) {
        try {
            channel.basicAck(tag, false);
        } catch (IOException ignored) {
        }
    }

    private void nackQuietly(Channel channel, long tag) {
        try {
            channel.basicNack(tag, false, true); // requeue=true 重回队列
        } catch (IOException ignored) {
        }
    }
}
