package com.example.hello.service;

import com.example.hello.document.JobPosition;
import com.example.hello.entity.StructuredResume;
import com.example.hello.es.JobPositionDoc;
import com.example.hello.es.JobPositionEsRepository;
import com.example.hello.repository.JobPositionRepository;
import com.example.hello.repository.StructuredResumeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Job matching service using Elasticsearch for search & scoring.
 *
 * Architecture (CQRS pattern):
 *   - MySQL: authoritative data store (writes)
 *   - Elasticsearch: search & matching engine (reads)
 *
 * Matching strategy:
 *   1. Bool Query filters: education, graduation time, major (hard constraints)
 *   2. Should clauses: skill matching (soft scoring via BM25)
 *   3. Results sorted by relevance score (descending)
 */
@Service
public class JobMatchingService {

    private static final Logger log = LoggerFactory.getLogger(JobMatchingService.class);

    private final JobPositionRepository jobPositionRepository;
    private final StructuredResumeRepository structuredResumeRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final JobPositionEsRepository jobPositionEsRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JobMatchingService(JobPositionRepository jobPositionRepository,
                               StructuredResumeRepository structuredResumeRepository,
                               ElasticsearchOperations elasticsearchOperations,
                               JobPositionEsRepository jobPositionEsRepository) {
        this.jobPositionRepository = jobPositionRepository;
        this.structuredResumeRepository = structuredResumeRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.jobPositionEsRepository = jobPositionEsRepository;
    }

    public List<JobPosition> getMatchedJobs(String resumeId, int page, int size) {
        if (resumeId == null || resumeId.isEmpty()) {
            return getAllJobs(page, size);
        }

        Optional<StructuredResume> resumeOpt = structuredResumeRepository.findByResumeId(resumeId);
        if (resumeOpt.isEmpty()) {
            log.info("Resume not found or not yet processed: resumeId={}, returning all jobs", resumeId);
            return getAllJobs(page, size);
        }

        StructuredResume resume = resumeOpt.get();
        List<String> userSkills = extractSkills(resume.getSkills());

        log.info("Searching ES for matching jobs: resumeId={}, education={}, major={}, skills={}",
                resumeId, resume.getEducation(), resume.getMajor(), userSkills);

        try {
            return searchWithElasticsearch(resume, userSkills, page, size);
        } catch (Exception e) {
            log.warn("Elasticsearch query failed, falling back to JPA: {}", e.getMessage());
            return fallbackJpaMatching(resume, userSkills, page, size);
        }
    }

    /**
     * Elasticsearch-based matching using Bool Query + relevance scoring.
     */
    private List<JobPosition> searchWithElasticsearch(StructuredResume resume,
                                                       List<String> userSkills,
                                                       int page, int size) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        // Filter: education level (hard constraint)
        if (resume.getEducation() != null && !resume.getEducation().isEmpty()) {
            List<String> acceptableEducations = getAcceptableEducations(resume.getEducation());
            List<FieldValue> fieldValues = acceptableEducations.stream()
                    .map(FieldValue::of)
                    .collect(Collectors.toList());
            boolBuilder.filter(f -> f.terms(t -> t
                    .field("educationRequirement")
                    .terms(tv -> tv.value(fieldValues))
            ));
        }

        // Filter: graduation time (hard constraint)
        if (resume.getGraduationTime() != null && !resume.getGraduationTime().isEmpty()) {
            boolBuilder.filter(f -> f.bool(b -> b
                    .should(s -> s.term(t -> t
                            .field("graduationTimeRange")
                            .value(resume.getGraduationTime())))
                    .should(s -> s.term(t -> t
                            .field("graduationTimeRange")
                            .value("不限")))
                    .minimumShouldMatch("1")
            ));
        }

        // Filter: major (hard constraint, with "不限专业" bypass)
        if (resume.getMajor() != null && !resume.getMajor().isEmpty()) {
            boolBuilder.filter(f -> f.bool(b -> b
                    .should(s -> s.match(m -> m
                            .field("targetMajor")
                            .query(resume.getMajor())))
                    .should(s -> s.term(t -> t
                            .field("targetMajor")
                            .value("不限专业")))
                    .minimumShouldMatch("1")
            ));
        }

        // Should: skill matching (soft scoring — more matched skills = higher score)
        for (String skill : userSkills) {
            boolBuilder.should(s -> s.term(t -> t
                    .field("skillsRequirement")
                    .value(skill)
                    .boost(2.0f)  // Boost skill matches
            ));
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(boolBuilder.build()))
                .withPageable(PageRequest.of(page, size))
                .build();

        SearchHits<JobPositionDoc> searchHits = elasticsearchOperations.search(query, JobPositionDoc.class);

        log.info("ES search returned {} hits (total: {})", searchHits.getSearchHits().size(),
                searchHits.getTotalHits());

        // Convert ES docs back to JPA entities (for API compatibility)
        List<Long> matchedIds = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(JobPositionDoc::getId)
                .collect(Collectors.toList());

        if (matchedIds.isEmpty()) {
            return new ArrayList<>();
        }

        // Fetch full entities from MySQL, preserving ES relevance order
        Map<Long, JobPosition> jobMap = new HashMap<>();
        jobPositionRepository.findAllById(matchedIds).forEach(job -> jobMap.put(job.getId(), job));

        return matchedIds.stream()
                .map(jobMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Fallback: in-memory matching when ES is unavailable.
     */
    private List<JobPosition> fallbackJpaMatching(StructuredResume resume,
                                                    List<String> userSkills,
                                                    int page, int size) {
        log.info("Using JPA fallback for job matching");

        Iterable<JobPosition> allJobs = jobPositionRepository.findAll();
        List<JobPositionScore> scoredJobs = new ArrayList<>();

        for (JobPosition job : allJobs) {
            if (!isEducationMatch(job.getEducationRequirement(), resume.getEducation())) continue;
            if (!isGraduationTimeMatch(job.getGraduationTimeRange(), resume.getGraduationTime())) continue;
            if (!isMajorMatch(job.getTargetMajor(), resume.getMajor())) continue;

            int score = calculateSkillScore(job.getSkillsRequirement(), userSkills);
            scoredJobs.add(new JobPositionScore(job, score));
        }

        scoredJobs.sort((a, b) -> Integer.compare(b.score, a.score));

        int from = page * size;
        if (from >= scoredJobs.size()) return new ArrayList<>();
        int to = Math.min(from + size, scoredJobs.size());

        return scoredJobs.subList(from, to).stream()
                .map(js -> js.job)
                .collect(Collectors.toList());
    }

    private List<JobPosition> getAllJobs(int page, int size) {
        List<JobPosition> jobs = new ArrayList<>();
        jobPositionRepository.findAll(PageRequest.of(page, size)).forEach(jobs::add);
        return jobs;
    }

    // =====================================================
    // Helper Methods
    // =====================================================

    /**
     * Returns all education levels that are <= the user's education.
     * e.g., if user has 硕士, they qualify for: 大专, 本科, 硕士
     */
    private List<String> getAcceptableEducations(String userEducation) {
        List<String> allLevels = List.of("大专", "本科", "硕士", "博士");
        int userLevel = getEducationLevel(userEducation);
        List<String> acceptable = new ArrayList<>();
        acceptable.add("不限");
        for (String level : allLevels) {
            if (getEducationLevel(level) <= userLevel) {
                acceptable.add(level);
            }
        }
        return acceptable;
    }

    private List<String> extractSkills(String skillsJson) {
        if (skillsJson == null || skillsJson.isEmpty()) return new ArrayList<>();
        try {
            return objectMapper.readValue(skillsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse skills JSON: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private boolean isEducationMatch(String required, String actual) {
        if (required == null || required.isEmpty() || required.equals("不限")) return true;
        if (actual == null || actual.isEmpty()) return false;
        return getEducationLevel(actual) >= getEducationLevel(required);
    }

    private int getEducationLevel(String edu) {
        return switch (edu) {
            case "大专" -> 1;
            case "本科" -> 2;
            case "硕士" -> 3;
            case "博士" -> 4;
            default -> 0;
        };
    }

    private boolean isGraduationTimeMatch(String required, String actual) {
        if (required == null || required.equals("不限") || required.isEmpty()) return true;
        if (actual == null || actual.isEmpty()) return true;
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
