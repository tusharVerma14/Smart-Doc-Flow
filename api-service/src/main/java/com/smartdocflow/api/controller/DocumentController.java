package com.smartdocflow.api.controller;

import com.smartdocflow.api.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {
    
    private final DocumentService documentService;
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("API Service is running");
    }
    
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId,
            @RequestParam("documentType") String documentType) {
        
        try {
            log.info("Upload request: user={}, type={}, file={}", 
                userId, documentType, file.getOriginalFilename());
            
            Map<String, Object> job = documentService.uploadDocument(userId, file, documentType);
            return ResponseEntity.ok(job);
            
        } catch (Exception e) {
            log.error("Upload failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable String jobId) {
        try {
            Map<String, Object> job = documentService.getJobStatus(jobId);
            return ResponseEntity.ok(job);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/users/{userId}/jobs")
    public ResponseEntity<List<Map<String, Object>>> getUserJobs(@PathVariable String userId) {
        List<Map<String, Object>> jobs = documentService.getUserJobs(userId);
        return ResponseEntity.ok(jobs);
    }
}
