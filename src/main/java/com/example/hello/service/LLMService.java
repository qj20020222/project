package com.example.hello.service;

import com.example.hello.document.JobPosition;
import com.example.hello.entity.StructuredResume;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class LLMService {

    @Value("${llm.api.key:sk-4a78acc5c40a4ce7b51604dffcb4304f}")
    private String apiKey;

    @Value("${llm.api.url:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
    private String apiUrl;

    @Value("${llm.model:qwen-plus}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StructuredResume extractStructuredData(String pdfText, String resumeId) {
        System.out.println("Calling LLM API to extract data for resume ID: " + resumeId);
        
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

            String requestBody = "{\n" +
                    "  \"model\": \"" + model + "\",\n" +
                    "  \"messages\": [\n" +
                    "    {\"role\": \"system\", \"content\": \"You are a helpful assistant that extracts structured data from resumes and outputs ONLY JSON.\"},\n" +
                    "    {\"role\": \"user\", \"content\": " + objectMapper.writeValueAsString(prompt) + "}\n" +
                    "  ],\n" +
                    "  \"temperature\": 0.1\n" +
                    "}";

            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String content = root.path("choices").get(0).path("message").path("content").asText();
                
                // Clean markdown formatting if LLM still returned it
                if (content.startsWith("```json")) {
                    content = content.substring(7);
                }
                if (content.endsWith("```")) {
                    content = content.substring(0, content.length() - 3);
                }
                
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
                
                return resume;
            }
        } catch (Exception e) {
            System.err.println("Failed to call LLM API: " + e.getMessage());
            System.err.println("Falling back to simulated parsing...");
            e.printStackTrace();
        }

        // Fallback or Mock data if API fails or network is blocked
        return generateMockResume(resumeId);
    }

    private StructuredResume generateMockResume(String resumeId) {
        StructuredResume resume = new StructuredResume();
        resume.setResumeId(resumeId);
        resume.setEducation("本科");
        resume.setGraduationTime("2024");
        resume.setMajor("计算机科学");
        resume.setSkills("[\"Java\", \"Spring Boot\", \"MySQL\"]");
        return resume;
    }

    // ---- Job Position Analysis (DeepSeek) ----

    private static final String DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String DEEPSEEK_API_KEY = "sk-4a78acc5c40a4ce7b51604dffcb4304f";
    private static final String DEEPSEEK_MODEL = "deepseek-chat";

    public String analyzeJob(JobPosition job) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(DEEPSEEK_API_KEY);

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

            String requestBody = "{\n" +
                "  \"model\": \"" + DEEPSEEK_MODEL + "\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"system\", \"content\": \"你是一位资深HR和职业规划师，擅长分析职位信息并给出专业建议。\"},\n" +
                "    {\"role\": \"user\", \"content\": " + objectMapper.writeValueAsString(prompt) + "}\n" +
                "  ],\n" +
                "  \"temperature\": 0.7\n" +
                "}";

            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                DEEPSEEK_API_URL, requestEntity, String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                return root.path("choices").get(0).path("message").path("content").asText();
            } else {
                 System.err.println("DeepSeek API Error Response: " + response.getBody());
            }
        } catch (Exception e) {
            System.err.println("DeepSeek analyzeJob failed: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("Insufficient Balance")) {
                return "API 账户余额不足，请充值后重试 (402 Payment Required)。";
            }
            return "AI 暂时无法获取分析，请稍后重试。(" + e.getMessage() + ")";
        }
        return "AI 暂时无法获取分析，请稍后重试。";
    }
}
