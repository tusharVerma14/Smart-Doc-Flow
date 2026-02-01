package com.smartdocflow.worker.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdocflow.worker.service.JobProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobQueueConsumer {

    private final RedisTemplate<String, String> redisTemplate;
    private final JobProcessingService jobProcessingService;
    private final ObjectMapper objectMapper;

    private static final String JOB_QUEUE = "document-jobs";
    private static final String DLQ_JOB_QUEUE = "document-jobs-dlq";
    private static final int BATCH_SIZE = 10;

    @Scheduled(fixedDelay = 1000)
    public void consumeJobs() {
        try {
            for (int i = 0; i < BATCH_SIZE; i++) {
                String jobJson = redisTemplate.opsForList().leftPop(JOB_QUEUE);
                if (jobJson != null) {
                    Map<String, Object> jobData = objectMapper.readValue(jobJson, Map.class);
                    log.info("Submitting job to thread pool: {}", jobData.get("jobId"));

                    processJobAsync(jobData);
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Error consuming jobs from queue", e);
        }
    }

    @Async("jobProcessorExecutor")
    public void processJobAsync(Map<String, Object> jobData) {
        try {
            jobProcessingService.processJob(jobData);
        } catch (Exception e) {
            int retry = (int) jobData.getOrDefault("retryCount", 0);

            if (retry < 3) {
                jobData.put("retryCount", retry + 1);
                log.warn("Retrying job {} (attempt {})", jobData.get("jobId"), retry + 1);
                pushJobToQueue(JOB_QUEUE, jobData);
            } else {
                log.error("Job {} moved to DLQ", jobData.get("jobId"), e);
                pushJobToQueue(DLQ_JOB_QUEUE, jobData);
            }
        }
    }

    private void pushJobToQueue(String queue, Map<String, Object> jobData) {
        try {
            redisTemplate.opsForList().rightPush(queue, objectMapper.writeValueAsString(jobData));
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize jobData for queue {}: {}", queue, jobData, ex);
        }
    }

}