package com.example.hello.es;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.util.List;

/**
 * Elasticsearch document for job positions.
 * Separate from JPA entity to follow CQRS pattern:
 *   - MySQL (JPA): writes / authoritative data store
 *   - Elasticsearch: reads / search & matching queries
 */
@Document(indexName = "job_positions")
@Setting(shards = 1, replicas = 0)
public class JobPositionDoc {

    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Keyword)
    private String location;

    @Field(type = FieldType.Keyword)
    private String educationRequirement;

    @Field(type = FieldType.Keyword)
    private List<String> skillsRequirement;

    @Field(type = FieldType.Keyword)
    private String salary;

    @Field(type = FieldType.Keyword)
    private String targetMajor;

    @Field(type = FieldType.Keyword)
    private String graduationTimeRange;

    public JobPositionDoc() {}

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
