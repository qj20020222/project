package com.example.hello.service;

import com.example.hello.document.JobPosition;
import com.example.hello.es.JobPositionDoc;
import com.example.hello.es.JobPositionEsRepository;
import com.example.hello.repository.JobPositionRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Seeds mock job data on startup and syncs to Elasticsearch.
 * Implements dual-write: MySQL (source of truth) + ES (search index).
 */
@Service
public class JobInitializerService {

    private static final Logger log = LoggerFactory.getLogger(JobInitializerService.class);

    private final JobPositionRepository jobPositionRepository;
    private final JobPositionEsRepository jobPositionEsRepository;

    public JobInitializerService(JobPositionRepository jobPositionRepository,
                                  JobPositionEsRepository jobPositionEsRepository) {
        this.jobPositionRepository = jobPositionRepository;
        this.jobPositionEsRepository = jobPositionEsRepository;
    }

    private static final String[] LOCATIONS = {"北京", "上海", "深圳", "广州", "杭州", "成都"};
    private static final String[] EDUCATIONS = {"本科", "硕士", "博士", "大专"};
    private static final String[][] SKILLS = {
        {"Java", "Spring Boot", "MySQL", "Redis"},
        {"Python", "Django", "Machine Learning", "PyTorch"},
        {"JavaScript", "React", "Vue", "CSS"},
        {"C++", "Linux", "Network", "Algorithm"},
        {"Go", "Docker", "Kubernetes", "Microservices"},
        {"Data Analysis", "Excel", "SQL", "Tableau"}
    };
    private static final String[] SALARIES = {"10k-15k", "15k-25k", "25k-40k", "40k-60k", "8k-12k"};
    private static final String[] MAJORS = {"计算机科学", "软件工程", "信息安全", "电子工程", "数学", "不限专业"};
    private static final String[] GRAD_TIMES = {"2023", "2024", "2025", "不限"};
    private static final String[] TITLES = {"后端开发工程师", "前端开发工程师", "算法工程师", "数据分析师", "C++研发工程师", "Go开发工程师"};

    @PostConstruct
    public void init() {
        // Step 1: Seed MySQL if empty
        if (jobPositionRepository.count() == 0) {
            log.info("MySQL is empty — seeding 500 mock job positions...");
            List<JobPosition> jobs = new ArrayList<>();
            Random random = new Random();

            for (int i = 0; i < 500; i++) {
                JobPosition job = new JobPosition();
                int titleIndex = random.nextInt(TITLES.length);
                job.setTitle(TITLES[titleIndex]);
                job.setLocation(LOCATIONS[random.nextInt(LOCATIONS.length)]);
                job.setEducationRequirement(EDUCATIONS[random.nextInt(EDUCATIONS.length)]);
                job.setSkillsRequirement(Arrays.asList(SKILLS[titleIndex % SKILLS.length]));
                job.setSalary(SALARIES[random.nextInt(SALARIES.length)]);
                job.setTargetMajor(MAJORS[random.nextInt(MAJORS.length)]);
                job.setGraduationTimeRange(GRAD_TIMES[random.nextInt(GRAD_TIMES.length)]);
                jobs.add(job);
            }

            jobPositionRepository.saveAll(jobs);
            log.info("MySQL: 500 job positions seeded successfully");
        } else {
            log.info("MySQL: {} job positions already exist", jobPositionRepository.count());
        }

        // Step 2: Sync MySQL → Elasticsearch (full re-index on startup)
        syncToElasticsearch();
    }

    /**
     * Full re-index: reads all jobs from MySQL and bulk-indexes into Elasticsearch.
     * This ensures ES is always consistent with MySQL on startup.
     */
    private void syncToElasticsearch() {
        try {
            log.info("Syncing job positions from MySQL to Elasticsearch...");

            List<JobPositionDoc> esDocs = new ArrayList<>();
            jobPositionRepository.findAll().forEach(job -> {
                JobPositionDoc doc = convertToEsDoc(job);
                esDocs.add(doc);
            });

            jobPositionEsRepository.saveAll(esDocs);
            log.info("Elasticsearch: {} documents indexed successfully", esDocs.size());

        } catch (Exception e) {
            log.error("Failed to sync to Elasticsearch (search will use JPA fallback): {}", e.getMessage());
        }
    }

    /**
     * Converts JPA entity to ES document (CQRS model mapping).
     */
    private JobPositionDoc convertToEsDoc(JobPosition job) {
        JobPositionDoc doc = new JobPositionDoc();
        doc.setId(job.getId());
        doc.setTitle(job.getTitle());
        doc.setLocation(job.getLocation());
        doc.setEducationRequirement(job.getEducationRequirement());
        doc.setSkillsRequirement(job.getSkillsRequirement());
        doc.setSalary(job.getSalary());
        doc.setTargetMajor(job.getTargetMajor());
        doc.setGraduationTimeRange(job.getGraduationTimeRange());
        return doc;
    }
}
