package com.example.hello.service;

import com.example.hello.config.RabbitMQConfig;
import com.example.hello.dto.ResumeProcessMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes resume processing tasks to RabbitMQ.
 * Messages are serialized as JSON and routed to the resume processing queue.
 */
@Service
public class RabbitMQProducer {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitMQProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendResumeUploadMessage(String resumeId, String filePath) {
        ResumeProcessMessage message = new ResumeProcessMessage(resumeId, filePath);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                message
        );

        log.info("Published resume processing message to RabbitMQ: resumeId={}", resumeId);
    }
}
