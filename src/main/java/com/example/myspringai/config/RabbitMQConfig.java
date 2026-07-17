package com.example.myspringai.config;

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
 * RabbitMQ 配置：Topic Exchange + 3个队列 + 死信队列 + 重试拦截器
 */
@Configuration
public class RabbitMQConfig {

    // ==================== 常量 ====================
    public static final String EXCHANGE_ALARM = "hikvision.alarm.exchange";
    public static final String QUEUE_DB = "alarm.db.queue";
    public static final String QUEUE_NOTIFY = "alarm.notify.queue";
    public static final String QUEUE_SMS = "alarm.sms.queue";
    public static final String ROUTING_KEY = "alarm.event";

    public static final String DLX_EXCHANGE = "alarm.dlx.exchange";
    public static final String DLQ_SMS = "alarm.sms.dlq";
    public static final String DLX_ROUTING_KEY = "alarm.sms.dlq";

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
    public Queue alarmDbQueue() {
        return QueueBuilder.durable(QUEUE_DB).build();
    }

    @Bean
    public Queue alarmNotifyQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFY).build();
    }

    @Bean
    public Queue alarmSmsQueue() {
        // 短信队列绑定死信交换机，消费失败 N 次后进入 DLQ
        return QueueBuilder.durable(QUEUE_SMS)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue alarmSmsDlq() {
        return QueueBuilder.durable(DLQ_SMS).build();
    }

    // ==================== Bindings ====================

    @Bean
    public Binding dbBinding() {
        return BindingBuilder.bind(alarmDbQueue()).to(alarmExchange()).with(ROUTING_KEY);
    }

    @Bean
    public Binding notifyBinding() {
        return BindingBuilder.bind(alarmNotifyQueue()).to(alarmExchange()).with(ROUTING_KEY);
    }

    @Bean
    public Binding smsBinding() {
        return BindingBuilder.bind(alarmSmsQueue()).to(alarmExchange()).with(ROUTING_KEY);
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(alarmSmsDlq()).to(dlxExchange()).with(DLX_ROUTING_KEY);
    }

    // ==================== JSON 序列化 ====================

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }

    // ==================== 短信重试拦截器 ====================

    /**
     * 失败后重试 3 次，每次间隔 5 秒。全部失败则 reject → 进入死信队列。
     */
    @Bean
    public RetryOperationsInterceptor smsRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(4)                          // 1次初始 + 3次重试
                .backOffOptions(5000, 1.0, 5000)         // 初始5s, 乘数1.0, 最大5s（固定间隔）
                .recoverer(new RejectAndDontRequeueRecoverer()) // 耗尽后不重回队列 → DLQ
                .build();
    }

    /**
     * 短信队列使用独立的 ListenerContainerFactory，注入重试拦截器
     */
    @Bean
    public RabbitListenerContainerFactory<?> smsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            RetryOperationsInterceptor smsRetryInterceptor) {

        var factory = new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setAdviceChain(smsRetryInterceptor);
        return factory;
    }
}
