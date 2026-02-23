package com.example.hello.document;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "job_position")
public class JobPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String title;

    @Column
    private String location;

    @Column
    private String educationRequirement;

    @ElementCollection
    private List<String> skillsRequirement;

    @Column
    private String salary;

    @Column
    private String targetMajor;

    @Column
    private String graduationTimeRange;

    // Default constructor
    public JobPosition() {}

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getEducationRequirement() { return educationRequirement; }
    public void setEducationRequirement(String educationRequirement) { this.educationRequirement = educationRequirement; }

    public List<String> getSkillsRequirement() { return skillsRequirement; }
    public void setSkillsRequirement(List<String> skillsRequirement) { this.skillsRequirement = skillsRequirement; }

    public String getSalary() { return salary; }
    public void setSalary(String salary) { this.salary = salary; }

    public String getTargetMajor() { return targetMajor; }
    public void setTargetMajor(String targetMajor) { this.targetMajor = targetMajor; }

    public String getGraduationTimeRange() { return graduationTimeRange; }
    public void setGraduationTimeRange(String graduationTimeRange) { this.graduationTimeRange = graduationTimeRange; }
}
