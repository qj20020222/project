package com.example.hello.es;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Elasticsearch repository for job position full-text search.
 */
@Repository
public interface JobPositionEsRepository extends ElasticsearchRepository<JobPositionDoc, Long> {

    List<JobPositionDoc> findByLocation(String location);

    List<JobPositionDoc> findByEducationRequirement(String educationRequirement);

    List<JobPositionDoc> findByTitleContaining(String keyword);
}
