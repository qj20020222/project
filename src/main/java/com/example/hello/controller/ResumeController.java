package com.example.hello.controller;

import com.example.hello.entity.ResumeMetadata;
import com.example.hello.repository.ResumeMetadataRepository;
import com.example.hello.service.RabbitMQProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    @Value("${resume.upload.dir:./uploads}")
    private String uploadDir;

    @Autowired
    private ResumeMetadataRepository resumeMetadataRepository;

    @Autowired
    private RabbitMQProducer rabbitMQProducer;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadResume(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || !file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest().body("Please upload a valid PDF file.");
        }

        try {
            // Ensure upload directory exists - Use absolute path to avoid Windows Tomcat temp dir issues
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Save file
            String resumeId = UUID.randomUUID().toString();
            String fileName = resumeId + ".pdf";
            Path targetLocation = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Save metadata to DB
            ResumeMetadata metadata = new ResumeMetadata();
            metadata.setResumeId(resumeId);
            metadata.setName(file.getOriginalFilename());
            metadata.setFilePath(targetLocation.toString());
            resumeMetadataRepository.save(metadata);

            // Send async message to Event Publisher
            rabbitMQProducer.sendResumeUploadMessage(resumeId, targetLocation.toString());

            Map<String, String> response = new HashMap<>();
            response.put("message", "Resume uploaded successfully. Processing in background.");
            response.put("resumeId", resumeId);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Could not upload the file: " + e.getMessage());
        }
    }
}
