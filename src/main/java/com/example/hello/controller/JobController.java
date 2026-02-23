package com.example.hello.controller;

import com.example.hello.document.JobPosition;
import com.example.hello.service.JobMatchingService;
import com.example.hello.service.LLMService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    @Autowired
    private JobMatchingService jobMatchingService;

    @Autowired
    private LLMService llmService;

    @GetMapping("/match")
    public List<JobPosition> getMatchedJobs(@RequestParam(value = "resumeId", required = false) String resumeId) {
        return jobMatchingService.getMatchedJobs(resumeId);
    }

    @PostMapping("/analyze")
    public Map<String, String> analyzeJob(@RequestBody JobPosition job) {
        String analysis = llmService.analyzeJob(job);
        return Map.of("analysis", analysis);
    }
}
