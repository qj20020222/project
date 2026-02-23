package com.example.hello.service;

import com.example.hello.document.JobPosition;
import com.example.hello.entity.StructuredResume;
import com.example.hello.repository.StructuredResumeRepository;
import com.example.hello.repository.JobPositionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobMatchingService {

    @Autowired
    private JobPositionRepository jobPositionRepository;

    @Autowired
    private StructuredResumeRepository structuredResumeRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<JobPosition> getMatchedJobs(String resumeId, int page, int size) {
        if (resumeId == null || resumeId.isEmpty()) {
            return getAllJobs(page, size);
        }

        Optional<StructuredResume> resumeOpt = structuredResumeRepository.findByResumeId(resumeId);
        if (resumeOpt.isEmpty()) {
            return getAllJobs(page, size);
        }

        StructuredResume resume = resumeOpt.get();
        List<String> userSkills = extractSkills(resume.getSkills());

        // 1. Filter jobs based on strict criteria: Education, GraduationTime, Major
        // Note: For simplicity, we fetch all and filter in memory, but ideally uses ES bool queries.
        // Let's use ES bool queries for filtering.
        // Actually, since this is a POC with 100 mock jobs, filtering in memory after fetching all is fine,
        // but let's try to do it right if possible, or simple ES match all and pure Java scoring.
        
        Iterable<JobPosition> allJobs = jobPositionRepository.findAll();
        List<JobPositionScore> scoredJobs = new ArrayList<>();

        for (JobPosition job : allJobs) {
            // Apply hard filters
            if (!isEducationMatch(job.getEducationRequirement(), resume.getEducation())) {
                continue;
            }
            if (!isGraduationTimeMatch(job.getGraduationTimeRange(), resume.getGraduationTime())) {
                continue;
            }
            if (!isMajorMatch(job.getTargetMajor(), resume.getMajor())) {
                continue;
            }

            // Calculate Score based on Skills
            int score = calculateSkillScore(job.getSkillsRequirement(), userSkills);
            scoredJobs.add(new JobPositionScore(job, score));
        }

        // Sort by score descending
        scoredJobs.sort((a, b) -> Integer.compare(b.score, a.score));

        // Group the top based on pagination
        int from = page * size;
        if (from >= scoredJobs.size()) return new ArrayList<>();
        int to = Math.min(from + size, scoredJobs.size());

        return scoredJobs.subList(from, to).stream()
                .map(js -> js.job)
                .collect(Collectors.toList());
    }

    private List<JobPosition> getAllJobs(int page, int size) {
        List<JobPosition> jobs = new ArrayList<>();
        // Fetch paginated generic jobs if no resume
        jobPositionRepository.findAll(PageRequest.of(page, size)).forEach(jobs::add);
        return jobs;
    }

    private List<String> extractSkills(String skillsJson) {
        if (skillsJson == null || skillsJson.isEmpty()) return new ArrayList<>();
        try {
            return objectMapper.readValue(skillsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private boolean isEducationMatch(String required, String actual) {
        if (required == null || required.isEmpty() || required.equals("不限")) return true;
        if (actual == null || actual.isEmpty()) return false;
        
        // Simple hierarchy: 博士 > 硕士 > 本科 > 大专
        int reqLvl = getEducationLevel(required);
        int actLvl = getEducationLevel(actual);
        return actLvl >= reqLvl;
    }

    private int getEducationLevel(String edu) {
        switch (edu) {
            case "大专": return 1;
            case "本科": return 2;
            case "硕士": return 3;
            case "博士": return 4;
            default: return 0;
        }
    }

    private boolean isGraduationTimeMatch(String required, String actual) {
        if (required == null || required.equals("不限") || required.isEmpty()) return true;
        if (actual == null || actual.isEmpty()) return true; // lenient if not specified
        return required.contains(actual);
    }

    private boolean isMajorMatch(String required, String actual) {
        if (required == null || required.equals("不限专业") || required.isEmpty()) return true;
        if (actual == null || actual.isEmpty()) return false;
        return required.contains(actual) || actual.contains(required);
    }

    private int calculateSkillScore(List<String> requiredSkills, List<String> userSkills) {
        if (requiredSkills == null || requiredSkills.isEmpty()) return 100;
        if (userSkills == null || userSkills.isEmpty()) return 0;

        int matchCount = 0;
        for (String req : requiredSkills) {
            for (String usr : userSkills) {
                if (req.equalsIgnoreCase(usr) || usr.toLowerCase().contains(req.toLowerCase())) {
                    matchCount++;
                    break;
                }
            }
        }
        return (int) (((double) matchCount / requiredSkills.size()) * 100);
    }

    private static class JobPositionScore {
        JobPosition job;
        int score;

        JobPositionScore(JobPosition job, int score) {
            this.job = job;
            this.score = score;
        }
    }
}
