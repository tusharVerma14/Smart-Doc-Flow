package com.smartdocflow.worker.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdocflow.worker.service.JobProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
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

    @Scheduled(fixedDelay = 1000) // every 1 second
    public void consumeJobs() {
        try {
            String jobJson = redisTemplate.opsForList().leftPop(JOB_QUEUE);
            if (jobJson != null) {
                Map<String, Object> jobData = objectMapper.readValue(jobJson, Map.class);
                log.info("Processing job from queue: {}", jobData.get("jobId"));
                jobProcessingService.processJob(jobData);
            }
        } catch (Exception e) {
            log.error("Error processing job from queue", e);
        }
    }
}
