package com.smartdocflow.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueProducerService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String JOB_QUEUE = "document-jobs";
    
    public void publishJob(Map<String, Object> jobData) throws JsonProcessingException {
        String jobJson = objectMapper.writeValueAsString(jobData);
        redisTemplate.opsForList().rightPush(JOB_QUEUE, jobJson); // store in list
        log.info("Enqueued job: {}", jobData.get("jobId"));
    }

    public long getQueueSize() {
        return redisTemplate.opsForList().size(JOB_QUEUE);
    }

    public String peekJob() {
        return redisTemplate.opsForList().index(JOB_QUEUE, 0); // peek first item
    }
}
