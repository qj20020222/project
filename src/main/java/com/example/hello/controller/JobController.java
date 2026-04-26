package com.example.hello.controller;

import com.example.hello.document.JobPosition;
import com.example.hello.dto.ApiResponse;
import com.example.hello.service.JobMatchingService;
import com.example.hello.service.LLMService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final JobMatchingService jobMatchingService;
    private final LLMService llmService;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public JobController(JobMatchingService jobMatchingService, LLMService llmService) {
        this.jobMatchingService = jobMatchingService;
        this.llmService = llmService;
    }

    @GetMapping("/match")
    public List<JobPosition> getMatchedJobs(
            @RequestParam(value = "resumeId", required = false) String resumeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return jobMatchingService.getMatchedJobs(resumeId, page, size);
    }

    /**
     * Standard (non-streaming) job analysis endpoint.
     * Returns complete analysis in one response.
     */
    @PostMapping("/analyze")
    public Map<String, String> analyzeJob(@RequestBody JobPosition job) {
        String analysis = llmService.analyzeJob(job);
        return Map.of("analysis", analysis);
    }

    /**
     * SSE streaming endpoint for real-time AI analysis.
     * Pushes analysis text character-by-character for typewriter effect.
     *
     * Frontend connects via: new EventSource('/api/jobs/analyze/stream?jobId=123')
     */
    @GetMapping(value = "/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeJobStream(@RequestParam Long jobId,
                                        @RequestParam(required = false) String title,
                                        @RequestParam(required = false) String location,
                                        @RequestParam(required = false) String salary) {

        SseEmitter emitter = new SseEmitter(120_000L); // 2 minute timeout

        sseExecutor.submit(() -> {
            try {
                // Build a minimal JobPosition for analysis
                JobPosition job = new JobPosition();
                job.setId(jobId);
                job.setTitle(title != null ? title : "未知职位");
                job.setLocation(location != null ? location : "未知");
                job.setSalary(salary != null ? salary : "面议");

                String analysis = llmService.analyzeJob(job);

                // Stream character by character for typewriter effect
                int chunkSize = 3; // Send 3 chars at a time for balance
                for (int i = 0; i < analysis.length(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, analysis.length());
                    String chunk = analysis.substring(i, end);
                    emitter.send(SseEmitter.event()
                            .name("chunk")
                            .data(chunk));
                    Thread.sleep(30); // 30ms delay between chunks
                }

                // Send completion event
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("[DONE]"));
                emitter.complete();

            } catch (Exception e) {
                log.error("SSE streaming failed: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("分析失败: " + e.getMessage()));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
