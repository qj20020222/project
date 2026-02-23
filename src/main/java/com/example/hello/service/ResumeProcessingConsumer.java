package com.example.hello.service;

import com.example.hello.entity.StructuredResume;
import com.example.hello.event.ResumeUploadEvent;
import com.example.hello.repository.StructuredResumeRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class ResumeProcessingConsumer {

    @Autowired
    private LLMService llmService;

    @Autowired
    private StructuredResumeRepository structuredResumeRepository;

    @Async
    @EventListener
    public void processResume(ResumeUploadEvent event) {
        String resumeId = event.getResumeId();
        String filePath = event.getFilePath();
        System.out.println("Received event to process resume. ID: " + resumeId + ", Path: " + filePath);

        try {
            File file = new File(filePath);
            if (!file.exists()) {
                System.err.println("File not found: " + filePath);
                return;
            }

            PDDocument document = Loader.loadPDF(file);
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String extractedText = pdfStripper.getText(document);
            document.close();

            System.out.println("Successfully extracted text from PDF.");

            StructuredResume structuredResume = llmService.extractStructuredData(extractedText, resumeId);

            if (structuredResume != null) {
                structuredResumeRepository.save(structuredResume);
                System.out.println("Successfully saved structured resume to DB.");
            }

        } catch (Exception e) {
            System.err.println("Failed to process resume: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
