package com.example.hello.service;

import com.example.hello.document.JobPosition;
import com.example.hello.repository.JobPositionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Service
public class JobInitializerService {

    @Autowired
    private JobPositionRepository jobPositionRepository;

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
        if (jobPositionRepository.count() == 0) {
            System.out.println("Database is empty. Initializing 500 mock job positions...");
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
            System.out.println("100 mock job positions have been initialized in Database.");
        } else {
            System.out.println("Job positions already exist in Database.");
        }
    }
}
