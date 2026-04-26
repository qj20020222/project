package com.example.hello.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ infrastructure configuration.
 *
 * Topology:
 *   resume.exchange (Direct) --[resume.process]--> resume.process.queue
 *                             --[resume.dlq]-----> resume.process.dlq
 *
 * Failed messages are routed to DLQ for manual inspection / retry.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "resume.exchange";
    public static final String QUEUE_NAME = "resume.process.queue";
    public static final String ROUTING_KEY = "resume.process";

    public static final String DLQ_EXCHANGE_NAME = "resume.dlq.exchange";
    public static final String DLQ_QUEUE_NAME = "resume.process.dlq";
    public static final String DLQ_ROUTING_KEY = "resume.dlq";

    // ---- Main Exchange & Queue ----

    @Bean
    public DirectExchange resumeExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_NAME)
                .durable(true)
                .build();
    }

    @Bean
    public Queue resumeProcessQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .withArgument("x-message-ttl", 300000) // 5 min TTL
                .build();
    }

    @Bean
    public Binding resumeBinding(Queue resumeProcessQueue, DirectExchange resumeExchange) {
        return BindingBuilder.bind(resumeProcessQueue)
                .to(resumeExchange)
                .with(ROUTING_KEY);
    }

    // ---- Dead Letter Queue ----

    @Bean
    public DirectExchange dlqExchange() {
        return ExchangeBuilder.directExchange(DLQ_EXCHANGE_NAME)
                .durable(true)
                .build();
    }

    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(DLQ_QUEUE_NAME).build();
    }

    @Bean
    public Binding dlqBinding(Queue dlqQueue, DirectExchange dlqExchange) {
        return BindingBuilder.bind(dlqQueue)
                .to(dlqExchange)
                .with(DLQ_ROUTING_KEY);
    }

    // ---- Message Converter (JSON serialization) ----

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                System.err.println("Message delivery to exchange FAILED: " + cause);
            }
        });
        template.setReturnsCallback(returned -> {
            System.err.println("Message returned from exchange: " + returned.getMessage()
                    + ", replyCode=" + returned.getReplyCode()
                    + ", replyText=" + returned.getReplyText());
        });
        template.setMandatory(true);
        return template;
    }
}
