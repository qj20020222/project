package com.example.hello.repository;

import com.example.hello.entity.StructuredResume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StructuredResumeRepository extends JpaRepository<StructuredResume, Long> {
    Optional<StructuredResume> findByResumeId(String resumeId);
}
