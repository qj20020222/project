package com.example.hello.service;

import com.example.hello.config.RabbitMQConfig;
import com.example.hello.dto.ResumeProcessMessage;
import com.example.hello.entity.ResumeMetadata;
import com.example.hello.entity.StructuredResume;
import com.example.hello.repository.ResumeMetadataRepository;
import com.example.hello.repository.StructuredResumeRepository;
import com.rabbitmq.client.Channel;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * Consumes resume processing messages from RabbitMQ.
 *
 * Pipeline: PDF → Text extraction → LLM structured data → DB persist
 * Uses manual ACK for message reliability — message is only removed
 * from queue after successful processing.
 * Failed messages are NACK'd and routed to Dead Letter Queue.
 */
@Service
public class ResumeProcessingConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResumeProcessingConsumer.class);

    private final LLMService llmService;
    private final StructuredResumeRepository structuredResumeRepository;
    private final ResumeMetadataRepository resumeMetadataRepository;

    public ResumeProcessingConsumer(LLMService llmService,
                                    StructuredResumeRepository structuredResumeRepository,
                                    ResumeMetadataRepository resumeMetadataRepository) {
        this.llmService = llmService;
        this.structuredResumeRepository = structuredResumeRepository;
        this.resumeMetadataRepository = resumeMetadataRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void processResume(ResumeProcessMessage message,
                               Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {

        String resumeId = message.getResumeId();
        String filePath = message.getFilePath();
        log.info("[MQ] Received resume processing task: resumeId={}, path={}", resumeId, filePath);

        // Update status: PARSING
        updateResumeStatus(resumeId, "PARSING");

        try {
            // Step 1: Extract text from PDF
            File file = new File(filePath);
            if (!file.exists()) {
                log.error("PDF file not found: {}", filePath);
                updateResumeStatus(resumeId, "FAILED");
                channel.basicNack(deliveryTag, false, false); // Send to DLQ
                return;
            }

            PDDocument document = Loader.loadPDF(file);
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String extractedText = pdfStripper.getText(document);
            document.close();
            log.info("[MQ] PDF text extracted successfully: resumeId={}, length={}", resumeId, extractedText.length());

            // Update status: ANALYZING (calling LLM)
            updateResumeStatus(resumeId, "ANALYZING");

            // Step 2: Call LLM to extract structured data
            StructuredResume structuredResume = llmService.extractStructuredData(extractedText, resumeId);

            if (structuredResume != null) {
                // Step 3: Persist to database
                structuredResumeRepository.save(structuredResume);
                updateResumeStatus(resumeId, "COMPLETED");
                log.info("[MQ] Resume processing completed: resumeId={}", resumeId);
            } else {
                updateResumeStatus(resumeId, "FAILED");
                log.warn("[MQ] LLM returned null for resumeId={}", resumeId);
            }

            // ACK: message processed successfully
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("[MQ] Failed to process resume: resumeId={}, error={}", resumeId, e.getMessage(), e);
            updateResumeStatus(resumeId, "FAILED");
            try {
                // NACK: reject message, don't requeue (will go to DLQ)
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception nackEx) {
                log.error("Failed to NACK message: {}", nackEx.getMessage());
            }
        }
    }

    /**
     * Update processing status in database (state machine transitions).
     * States: UPLOADED → PARSING → ANALYZING → COMPLETED | FAILED
     */
    private void updateResumeStatus(String resumeId, String status) {
        try {
            resumeMetadataRepository.findByResumeId(resumeId).ifPresent(metadata -> {
                metadata.setStatus(status);
                resumeMetadataRepository.save(metadata);
                log.debug("Resume status updated: resumeId={}, status={}", resumeId, status);
            });
        } catch (Exception e) {
            log.warn("Failed to update resume status: resumeId={}, status={}, error={}",
                    resumeId, status, e.getMessage());
        }
    }
}
