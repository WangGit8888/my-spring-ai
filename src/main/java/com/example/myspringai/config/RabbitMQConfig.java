package com.example.myspringai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
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
@Slf4j
@Configuration
public class RabbitMQConfig {

    // ==================== 常量 ====================
    public static final String EXCHANGE_ALARM = "hikvision.alarm.exchange";
    public static final String QUEUE_DB = "alarm.db.queue";
    public static final String QUEUE_NOTIFY = "alarm.notify.queue";
    public static final String QUEUE_SMS = "alarm.sms.queue";
    public static final String ROUTING_KEY = "alarm.event";

    public static final String DLX_EXCHANGE = "alarm.dlx.exchange";
    public static final String DLQ_DB = "alarm.db.dlq";
    public static final String DLQ_NOTIFY = "alarm.notify.dlq";
    public static final String DLQ_SMS = "alarm.sms.dlq";
    public static final String DLX_RK_DB = "alarm.db.dlq";
    public static final String DLX_RK_NOTIFY = "alarm.notify.dlq";
    public static final String DLX_RK_SMS = "alarm.sms.dlq";


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
    public Queue alarmDbQueue() {
        return QueueBuilder.durable(QUEUE_DB)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLX_RK_DB)
                .build();
    }

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
    public Queue alarmDbDlq() {
        return QueueBuilder.durable(DLQ_DB).build();
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
    public Binding dlqDbBinding() {
        return BindingBuilder.bind(alarmDbDlq()).to(dlxExchange()).with(DLX_RK_DB);
    }

    @Bean
    public Binding dlqNotifyBinding() {
        return BindingBuilder.bind(alarmNotifyDlq()).to(dlxExchange()).with(DLX_RK_NOTIFY);
    }

    @Bean
    public Binding dlqSmsBinding() {
        return BindingBuilder.bind(alarmSmsDlq()).to(dlxExchange()).with(DLX_RK_SMS);
    }

    // ==================== JSON 序列化 ====================

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter jackson2JsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jackson2JsonMessageConverter);
        // 消息无法路由到队列时触发（需要配合 mandatory=true + publisher-returns=true）
        template.setMandatory(true);
        template.setReturnsCallback(returned -> {
            log.error("消息路由失败! exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText());
        });
        // publisher confirm 回调：broker 真正收到消息后，完成 CorrelationData 里的 Future
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (correlationData != null) {
                if (ack) {
                    correlationData.getFuture().complete(
                            new CorrelationData.Confirm(true, null));
                } else {
                    correlationData.getFuture().complete(
                            new CorrelationData.Confirm(false, cause));
                }
            }
        });
        return template;
    }

    // ==================== 重试拦截器 ====================

    /**
     * 短信：失败后重试 3 次，每次间隔 5 秒。全部失败则 reject → DLQ
     */
    @Bean
    public RetryOperationsInterceptor smsRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(4)
                .backOffOptions(5000, 1.0, 5000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    /**
     * 站内信：失败后重试 3 次，每次间隔 5 秒。全部失败则 reject → DLQ
     */
    @Bean
    public RetryOperationsInterceptor notifyRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(4)
                .backOffOptions(5000, 1.0, 5000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    // ==================== ListenerContainerFactory ====================

    @Bean
    public RabbitListenerContainerFactory<?> smsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            RetryOperationsInterceptor smsRetryInterceptor,
            Jackson2JsonMessageConverter jackson2JsonMessageConverter) {

        var factory = new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setMessageConverter(jackson2JsonMessageConverter);
        factory.setAdviceChain(smsRetryInterceptor);
        return factory;
    }

    @Bean
    public RabbitListenerContainerFactory<?> notifyListenerContainerFactory(
            ConnectionFactory connectionFactory,
            RetryOperationsInterceptor notifyRetryInterceptor,
            Jackson2JsonMessageConverter jackson2JsonMessageConverter) {

        var factory = new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setMessageConverter(jackson2JsonMessageConverter);
        factory.setAdviceChain(notifyRetryInterceptor);
        return factory;
    }
}
