package com.example.hello.service;

import com.example.hello.document.JobPosition;
import com.example.hello.entity.StructuredResume;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * LLM integration service with production-grade resilience patterns.
 *
 * Features:
 *   - Circuit Breaker: stops calling LLM when failure rate > 50%
 *   - Retry: exponential backoff (2s → 4s → 8s) on transient failures
 *   - Rate Limiter: max 10 calls/second to avoid API throttling
 *   - Redis Cache: LLM analysis results cached for 24h
 *   - Graceful Fallback: returns mock data when LLM is unavailable
 */
@Service
public class LLMService {

    private static final Logger log = LoggerFactory.getLogger(LLMService.class);

    @Value("${llm.api.key}")
    private String apiKey;

    @Value("${llm.api.url}")
    private String apiUrl;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.deepseek.api.key}")
    private String deepseekApiKey;

    @Value("${llm.deepseek.api.url}")
    private String deepseekApiUrl;

    @Value("${llm.deepseek.model}")
    private String deepseekModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String ANALYSIS_CACHE_PREFIX = "llm:analysis:";
    private static final long ANALYSIS_CACHE_TTL_HOURS = 24;

    public LLMService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // =====================================================
    // Resume Structured Data Extraction (Qwen)
    // =====================================================

    @CircuitBreaker(name = "llmService", fallbackMethod = "extractStructuredDataFallback")
    @Retry(name = "llmService")
    @RateLimiter(name = "llmService")
    public StructuredResume extractStructuredData(String pdfText, String resumeId) {
        log.info("Calling LLM API to extract structured data: resumeId={}", resumeId);
        long startTime = System.currentTimeMillis();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            String prompt = "Please extract the following information from the provided resume text and return it ONLY as a valid JSON object without any markdown formatting. The JSON object must have these exact keys:\n" +
                    "- \"education\" (string, e.g., '本科', '硕士')\n" +
                    "- \"graduationTime\" (string, e.g., '2024')\n" +
                    "- \"major\" (string, e.g., '计算机科学')\n" +
                    "- \"skills\" (array of strings, e.g., ['Java', 'Spring'])\n\n" +
                    "Resume text:\n" + pdfText;

            // Build request body using ObjectMapper (not manual string concat)
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.1);

            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode systemMsg = messages.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "You are a helpful assistant that extracts structured data from resumes and outputs ONLY JSON.");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);

            HttpEntity<String> requestEntity = new HttpEntity<>(
                    objectMapper.writeValueAsString(requestBody), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String content = root.path("choices").get(0).path("message").path("content").asText();

                // Clean markdown formatting if LLM still returned it
                content = cleanMarkdownJson(content);

                JsonNode jsonResponse = objectMapper.readTree(content.trim());

                StructuredResume resume = new StructuredResume();
                resume.setResumeId(resumeId);
                resume.setEducation(jsonResponse.path("education").asText("未知"));
                resume.setGraduationTime(jsonResponse.path("graduationTime").asText("未知"));
                resume.setMajor(jsonResponse.path("major").asText("未知"));

                List<String> skills = new ArrayList<>();
                if (jsonResponse.path("skills").isArray()) {
                    for (JsonNode skillNode : jsonResponse.path("skills")) {
                        skills.add(skillNode.asText());
                    }
                }
                resume.setSkills(objectMapper.writeValueAsString(skills));

                long elapsed = System.currentTimeMillis() - startTime;
                log.info("LLM extraction completed: resumeId={}, elapsed={}ms", resumeId, elapsed);
                return resume;
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("LLM API call failed: resumeId={}, elapsed={}ms, error={}", resumeId, elapsed, e.getMessage());
            throw new RuntimeException("LLM API call failed: " + e.getMessage(), e);
        }

        throw new RuntimeException("LLM API returned unexpected response");
    }

    /**
     * Fallback method when circuit breaker is OPEN or all retries exhausted.
     * Returns mock data so the system remains functional.
     */
    public StructuredResume extractStructuredDataFallback(String pdfText, String resumeId, Throwable t) {
        log.warn("LLM fallback activated for resume extraction: resumeId={}, reason={}", resumeId, t.getMessage());
        StructuredResume resume = new StructuredResume();
        resume.setResumeId(resumeId);
        resume.setEducation("本科");
        resume.setGraduationTime("2024");
        resume.setMajor("计算机科学");
        resume.setSkills("[\"Java\", \"Spring Boot\", \"MySQL\"]");
        return resume;
    }

    // =====================================================
    // Job Position Analysis (DeepSeek) — with Redis Cache
    // =====================================================

    @CircuitBreaker(name = "llmService", fallbackMethod = "analyzeJobFallback")
    @Retry(name = "llmService")
    @RateLimiter(name = "llmService")
    public String analyzeJob(JobPosition job) {
        String cacheKey = ANALYSIS_CACHE_PREFIX + job.getId();

        // Check Redis cache first
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("Cache HIT for job analysis: jobId={}", job.getId());
                return cached.toString();
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed, proceeding without cache: {}", e.getMessage());
        }

        log.info("Cache MISS — calling DeepSeek API for job analysis: jobId={}, title={}", job.getId(), job.getTitle());
        long startTime = System.currentTimeMillis();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(deepseekApiKey);

            String skillsList = job.getSkillsRequirement() != null
                    ? String.join(", ", job.getSkillsRequirement()) : "无";

            String prompt = String.format(
                "请对以下职位进行深度分析，用中文回答，分为以下几个方面：\n" +
                "1. 🌟 发展前景：该职位和行业的长期发展趋势如何？\n" +
                "2. 💰 薪资水平：该薪资区间在行业内处于什么水平？留有多大增长空间？\n" +
                "3. 🏆 竞争难度：招聘要求的门槛如何？竞争激烈程度如何？\n" +
                "4. 💡 求职建议：针对这个职位，求职者应该重点准备哪些方面？\n\n" +
                "职位信息：\n" +
                "- 职位名称：%s\n" +
                "- 工作地点：%s\n" +
                "- 薪资范围：%s\n" +
                "- 学历要求：%s\n" +
                "- 专业要求：%s\n" +
                "- 技能要求：%s\n" +
                "- 毕业时间范围：%s届\n\n" +
                "请给出详细、务实、有参考价值的分析，每个方面2-3句话。",
                job.getTitle(), job.getLocation(), job.getSalary(),
                job.getEducationRequirement(), job.getTargetMajor(),
                skillsList, job.getGraduationTimeRange()
            );

            // Build request body using ObjectMapper
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", deepseekModel);
            requestBody.put("temperature", 0.7);

            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode systemMsg = messages.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是一位资深HR和职业规划师，擅长分析职位信息并给出专业建议。");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);

            HttpEntity<String> requestEntity = new HttpEntity<>(
                    objectMapper.writeValueAsString(requestBody), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                deepseekApiUrl, requestEntity, String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String analysis = root.path("choices").get(0).path("message").path("content").asText();

                // Cache result in Redis (TTL = 24h)
                try {
                    redisTemplate.opsForValue().set(cacheKey, analysis, ANALYSIS_CACHE_TTL_HOURS, TimeUnit.HOURS);
                    log.info("Cached job analysis in Redis: jobId={}, ttl={}h", job.getId(), ANALYSIS_CACHE_TTL_HOURS);
                } catch (Exception e) {
                    log.warn("Redis cache write failed: {}", e.getMessage());
                }

                long elapsed = System.currentTimeMillis() - startTime;
                log.info("DeepSeek analysis completed: jobId={}, elapsed={}ms", job.getId(), elapsed);
                return analysis;
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("DeepSeek API call failed: jobId={}, elapsed={}ms, error={}", job.getId(), elapsed, e.getMessage());
            throw new RuntimeException("DeepSeek API call failed: " + e.getMessage(), e);
        }

        throw new RuntimeException("DeepSeek API returned unexpected response");
    }

    /**
     * Fallback when circuit breaker is OPEN for job analysis.
     */
    public String analyzeJobFallback(JobPosition job, Throwable t) {
        log.warn("LLM fallback activated for job analysis: jobId={}, reason={}", job.getId(), t.getMessage());

        if (t.getMessage() != null && t.getMessage().contains("Insufficient Balance")) {
            return "API 账户余额不足，请充值后重试 (402 Payment Required)。";
        }
        return "AI 分析服务暂时不可用（熔断器已激活），请稍后重试。\n原因：" + t.getMessage();
    }

    // =====================================================
    // Utility
    // =====================================================

    private String cleanMarkdownJson(String content) {
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }
        return content.trim();
    }
}
