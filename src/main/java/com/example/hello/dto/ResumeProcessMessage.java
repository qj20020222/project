package com.example.hello.dto;

import java.io.Serializable;

/**
 * Message DTO for RabbitMQ resume processing pipeline.
 * Serialized as JSON via Jackson2JsonMessageConverter.
 */
public class ResumeProcessMessage implements Serializable {

    private String resumeId;
    private String filePath;

    public ResumeProcessMessage() {}

    public ResumeProcessMessage(String resumeId, String filePath) {
        this.resumeId = resumeId;
        this.filePath = filePath;
    }

    public String getResumeId() { return resumeId; }
    public void setResumeId(String resumeId) { this.resumeId = resumeId; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    @Override
    public String toString() {
        return "ResumeProcessMessage{resumeId='" + resumeId + "', filePath='" + filePath + "'}";
    }
}
