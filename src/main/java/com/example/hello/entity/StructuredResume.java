package com.example.hello.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "structured_resume")
public class StructuredResume {
 
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String resumeId;

    @Column
    private String education;

    @Column
    private String graduationTime;

    @Column
    private String major;

    @Column(columnDefinition = "TEXT")
    private String skills; // Stored as JSON string

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getResumeId() { return resumeId; }
    public void setResumeId(String resumeId) { this.resumeId = resumeId; }

    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }

    public String getGraduationTime() { return graduationTime; }
    public void setGraduationTime(String graduationTime) { this.graduationTime = graduationTime; }

    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }

    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
}
