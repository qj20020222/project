package com.example.hello.event;

import org.springframework.context.ApplicationEvent;

public class ResumeUploadEvent extends ApplicationEvent {
    private final String resumeId;
    private final String filePath;

    public ResumeUploadEvent(Object source, String resumeId, String filePath) {
        super(source);
        this.resumeId = resumeId;
        this.filePath = filePath;
    }

    public String getResumeId() { return resumeId; }
    public String getFilePath() { return filePath; }
}
