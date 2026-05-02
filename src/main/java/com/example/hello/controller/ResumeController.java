package com.example.hello.controller;

import com.example.hello.dto.ApiResponse;
import com.example.hello.entity.ResumeMetadata;
import com.example.hello.exception.BusinessException;
import com.example.hello.repository.ResumeMetadataRepository;
import com.example.hello.service.RabbitMQProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private static final Logger log = LoggerFactory.getLogger(ResumeController.class);

    @Value("${resume.upload.dir:./uploads}")
    private String uploadDir;

    private final ResumeMetadataRepository resumeMetadataRepository;
    private final RabbitMQProducer rabbitMQProducer;

    public ResumeController(ResumeMetadataRepository resumeMetadataRepository,
                             RabbitMQProducer rabbitMQProducer) {
        this.resumeMetadataRepository = resumeMetadataRepository;
        this.rabbitMQProducer = rabbitMQProducer;
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadResume(@RequestParam("file") MultipartFile file) {
        // Validate file
        if (file.isEmpty()) {
            throw new BusinessException(400, "File is empty");
        }
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            throw new BusinessException(400, "Only PDF files are accepted");
        }

        try {
            byte[] fileBytes = file.getBytes();
            String fileHash = calculateSha256(fileBytes);

            var existingResume = resumeMetadataRepository.findByFileHash(fileHash);
            if (existingResume.isPresent()) {
                ResumeMetadata metadata = existingResume.get();
                if ("FAILED".equals(metadata.getStatus())) {
                    metadata.setStatus("UPLOADED");
                    resumeMetadataRepository.save(metadata);
                    rabbitMQProducer.sendResumeUploadMessage(metadata.getResumeId(), metadata.getFilePath());
                }

                Map<String, String> data = new HashMap<>();
                data.put("resumeId", metadata.getResumeId());
                data.put("status", metadata.getStatus());
                data.put("duplicate", "true");

                log.info("Duplicate resume upload detected: resumeId={}, file={}, hash={}",
                        metadata.getResumeId(), file.getOriginalFilename(), fileHash);
                return ResponseEntity.ok(ApiResponse.success("Resume already exists", data));
            }

            // Ensure upload directory exists
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Save file
            String resumeId = UUID.randomUUID().toString();
            String fileName = resumeId + ".pdf";
            Path targetLocation = uploadPath.resolve(fileName);
            Files.write(targetLocation, fileBytes, StandardOpenOption.CREATE_NEW);

            // Save metadata to DB with initial status
            ResumeMetadata metadata = new ResumeMetadata();
            metadata.setResumeId(resumeId);
            metadata.setName(file.getOriginalFilename());
            metadata.setFilePath(targetLocation.toString());
            metadata.setFileHash(fileHash);
            metadata.setStatus("UPLOADED");
            resumeMetadataRepository.save(metadata);

            // Publish to RabbitMQ for async processing
            rabbitMQProducer.sendResumeUploadMessage(resumeId, targetLocation.toString());

            Map<String, String> data = new HashMap<>();
            data.put("resumeId", resumeId);
            data.put("status", "UPLOADED");

            log.info("Resume uploaded and queued for processing: resumeId={}, file={}",
                    resumeId, file.getOriginalFilename());
            return ResponseEntity.ok(ApiResponse.success("Resume uploaded successfully", data));

        } catch (IOException e) {
            log.error("File upload failed: {}", e.getMessage(), e);
            throw new BusinessException(500, "Could not upload the file: " + e.getMessage());
        }
    }

    private String calculateSha256(byte[] fileBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(fileBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    /**
     * Query the processing status of a resume.
     * Frontend can poll this endpoint to track async progress.
     *
     * States: UPLOADED → PARSING → ANALYZING → COMPLETED | FAILED
     */
    @GetMapping("/{resumeId}/status")
    public ResponseEntity<ApiResponse<Map<String, String>>> getResumeStatus(@PathVariable String resumeId) {
        return resumeMetadataRepository.findByResumeId(resumeId)
                .map(metadata -> {
                    Map<String, String> data = new HashMap<>();
                    data.put("resumeId", resumeId);
                    data.put("status", metadata.getStatus());
                    data.put("fileName", metadata.getName());
                    return ResponseEntity.ok(ApiResponse.success(data));
                })
                .orElseThrow(() -> new BusinessException(404, "Resume not found: " + resumeId));
    }
}
