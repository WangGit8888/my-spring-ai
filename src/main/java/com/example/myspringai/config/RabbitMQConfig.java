package com.example.myspringai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/**
 * RabbitMQ 配置：1 个 Topic Exchange + 2 个队列（通知 + 短信）+ 死信 + 重试。
 * <p>
 * 入口落库后发到 alarm.exchange，通知和短信各自独立消费，互不影响。
 * <p>
 * 消费者采用 MANUAL ACK：<br>
 * Level 3 → 发送成功 ACK，失败抛异常 → 重试拦截器重试 3 次 → 耗尽进 DLQ<br>
 * Level 1/2 → 直接 ACK，DB 保持 PENDING，由定时任务批量发送
 */
@Configuration
public class RabbitMQConfig {

    // ==================== 常量 ====================
    public static final String EXCHANGE_ALARM = "alarm.exchange";
    public static final String QUEUE_NOTIFY = "alarm.notify.queue";
    public static final String QUEUE_SMS = "alarm.sms.queue";
    public static final String ROUTING_KEY = "alarm.event";

    public static final String DLX_EXCHANGE = "alarm.dlx.exchange";
    public static final String DLQ_NOTIFY = "alarm.notify.dlq";
    public static final String DLQ_SMS = "alarm.sms.dlq";
    public static final String DLX_RK_NOTIFY = "alarm.notify.dlq";
    public static final String DLX_RK_SMS = "alarm.sms.dlq";

    // ==================== JSON 序列化 ====================

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    // ==================== Exchange ====================

    @Bean
    public TopicExchange alarmExchange() {
        return new TopicExchange(EXCHANGE_ALARM);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    // ==================== Queues ====================

    @Bean
    public Queue alarmNotifyQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFY)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLX_RK_NOTIFY)
                .build();
    }

    @Bean
    public Queue alarmSmsQueue() {
        return QueueBuilder.durable(QUEUE_SMS)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLX_RK_SMS)
                .build();
    }

    @Bean
    public Queue alarmNotifyDlq() {
        return QueueBuilder.durable(DLQ_NOTIFY).build();
    }

    @Bean
    public Queue alarmSmsDlq() {
        return QueueBuilder.durable(DLQ_SMS).build();
    }

    // ==================== Bindings ====================

    @Bean
    public Binding notifyBinding() {
        return BindingBuilder.bind(alarmNotifyQueue()).to(alarmExchange()).with(ROUTING_KEY);
    }

    @Bean
    public Binding smsBinding() {
        return BindingBuilder.bind(alarmSmsQueue()).to(alarmExchange()).with(ROUTING_KEY);
    }

    @Bean
    public Binding dlqNotifyBinding() {
        return BindingBuilder.bind(alarmNotifyDlq()).to(dlxExchange()).with(DLX_RK_NOTIFY);
    }

    @Bean
    public Binding dlqSmsBinding() {
        return BindingBuilder.bind(alarmSmsDlq()).to(dlxExchange()).with(DLX_RK_SMS);
    }

    // ==================== RabbitTemplate ====================

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter jackson2JsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jackson2JsonMessageConverter);
        return template;
    }

    // ==================== 重试拦截器 ====================

    /** 站内信：失败重试 3 次，间隔 5 秒，耗尽 reject → DLQ */
    @Bean
    public RetryOperationsInterceptor notifyRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(4)
                .backOffOptions(5000, 1.0, 5000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    /** 短信：失败重试 3 次，间隔 5 秒，耗尽 reject → DLQ */
    @Bean
    public RetryOperationsInterceptor smsRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(4)
                .backOffOptions(5000, 1.0, 5000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    // ==================== ListenerContainerFactory（MANUAL ACK） ====================

    /** 站内信：MANUAL ack + 重试拦截器 + 60 并发 */
    @Bean
    public RabbitListenerContainerFactory<?> notifyListenerContainerFactory(
            ConnectionFactory connectionFactory,
            RetryOperationsInterceptor notifyRetryInterceptor,
            Jackson2JsonMessageConverter jackson2JsonMessageConverter) {

        var factory = new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setMessageConverter(jackson2JsonMessageConverter);
        factory.setAdviceChain(notifyRetryInterceptor);
        factory.setConcurrentConsumers(60);
        factory.setPrefetchCount(80);
        return factory;
    }

    /** 短信：MANUAL ack + 重试拦截器 + 60 并发 */
    @Bean
    public RabbitListenerContainerFactory<?> smsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            RetryOperationsInterceptor smsRetryInterceptor,
            Jackson2JsonMessageConverter jackson2JsonMessageConverter) {

        var factory = new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setMessageConverter(jackson2JsonMessageConverter);
        factory.setAdviceChain(smsRetryInterceptor);
        factory.setConcurrentConsumers(60);
        factory.setPrefetchCount(80);
        return factory;
    }
}
