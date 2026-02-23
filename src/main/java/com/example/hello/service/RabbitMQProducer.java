package com.example.hello.service;

import com.example.hello.event.ResumeUploadEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class RabbitMQProducer {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public void sendResumeUploadMessage(String resumeId, String filePath) {
        // Now using Spring Events instead of RabbitMQ
        eventPublisher.publishEvent(new ResumeUploadEvent(this, resumeId, filePath));
        System.out.println("Published resume processing event for ID: " + resumeId);
    }
}
