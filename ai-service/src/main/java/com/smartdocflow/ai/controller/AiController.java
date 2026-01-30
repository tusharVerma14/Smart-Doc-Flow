package com.smartdocflow.ai.controller;

import com.smartdocflow.ai.dto.ExtractedDataDTO;
import com.smartdocflow.ai.service.DocumentProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final DocumentProcessingService processingService;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AI Service is running");
    }

    @PostMapping("/extract")
    public ResponseEntity<ExtractedDataDTO> extractData(
            @RequestParam String jobId,
            @RequestParam String filePath,  // This is MinIO object name now
            @RequestParam String documentType) {

        log.info("Extract request: jobId={}, type={}, minioPath={}",
                jobId, documentType, filePath);

        try {
            ExtractedDataDTO result = processingService.processDocument(
                    jobId, filePath, documentType);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Extraction failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}