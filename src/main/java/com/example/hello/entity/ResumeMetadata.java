package com.example.hello.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "resume_metadata")
public class ResumeMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String resumeId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String filePath;

    @Column(length = 64, unique = true)
    private String fileHash;

    @Column(nullable = false)
    private LocalDateTime uploadTime;

    /**
     * Processing status — state machine:
     *   UPLOADED → PARSING → ANALYZING → COMPLETED
     *                                  → FAILED
     */
    @Column(nullable = false, length = 20)
    private String status = "UPLOADED";

    @PrePersist
    protected void onCreate() {
        uploadTime = LocalDateTime.now();
        if (status == null) {
            status = "UPLOADED";
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getResumeId() { return resumeId; }
    public void setResumeId(String resumeId) { this.resumeId = resumeId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }

    public LocalDateTime getUploadTime() { return uploadTime; }
    public void setUploadTime(LocalDateTime uploadTime) { this.uploadTime = uploadTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
