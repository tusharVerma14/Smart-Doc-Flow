package com.smartdocflow.api.service;

import com.smartdocflow.api.entity.DocumentJob;
import com.smartdocflow.api.repository.DocumentJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentJobRepository jobRepository;
    private final QueueProducerService queueProducerService;
    private final MinioService minioService;

    @Transactional
    public Map<String, Object> uploadDocument(
            String userId,
            MultipartFile file,
            String documentType) throws IOException {

        // Generate unique job ID
        String jobId = UUID.randomUUID().toString();

        // Upload file to MinIO
        String minioObjectName = minioService.uploadFile(file, jobId);
        String minioUrl = minioService.getFileUrl(minioObjectName);

        log.info("File uploaded to MinIO: {} for job: {}", minioObjectName, jobId);

        // Create job entity
        String originalFilename = file.getOriginalFilename();
        DocumentJob job = DocumentJob.builder()
                .jobId(jobId)
                .userId(userId)
                .documentType(documentType)
                .filePath(minioObjectName)  // Store MinIO object name
                .fileName(originalFilename)
                .status("PENDING")
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        jobRepository.save(job);

        // Create job data for queue
        Map<String, Object> jobData = new HashMap<>();
        jobData.put("jobId", jobId);
        jobData.put("userId", userId);
        jobData.put("documentType", documentType);
        jobData.put("filePath", minioObjectName);  // Send MinIO path to worker
        jobData.put("minioUrl", minioUrl);
        jobData.put("fileName", originalFilename);
        jobData.put("status", "QUEUED");
        jobData.put("createdAt", LocalDateTime.now().toString());

        // Publish to queue
        queueProducerService.publishJob(jobData);

        // Update status
        job.setStatus("QUEUED");
        jobRepository.save(job);

        return jobData;
    }

    public Map<String, Object> getJobStatus(String jobId) {
        DocumentJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
        return toMap(job);
    }

    public List<Map<String, Object>> getUserJobs(String userId) {
        return jobRepository.findByUserId(userId)
                .stream()
                .map(this::toMap)
                .toList();
    }

    private Map<String, Object> toMap(DocumentJob job) {
        Map<String, Object> map = new HashMap<>();
        map.put("jobId", job.getJobId());
        map.put("userId", job.getUserId());
        map.put("documentType", job.getDocumentType());
        map.put("fileName", job.getFileName());
        map.put("status", job.getStatus());
        map.put("finalDecision", job.getFinalDecision());
        map.put("createdAt", job.getCreatedAt());
        map.put("updatedAt", job.getUpdatedAt());
        return map;
    }
}