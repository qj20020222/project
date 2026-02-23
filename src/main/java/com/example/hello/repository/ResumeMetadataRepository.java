package com.example.hello.repository;

import com.example.hello.entity.ResumeMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResumeMetadataRepository extends JpaRepository<ResumeMetadata, Long> {
    Optional<ResumeMetadata> findByResumeId(String resumeId);
}
