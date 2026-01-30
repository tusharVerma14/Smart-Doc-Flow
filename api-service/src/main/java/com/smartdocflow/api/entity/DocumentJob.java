package com.smartdocflow.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentJob {
    
    @Id
    private String jobId;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String documentType;
    
    @Column(nullable = false)
    private String filePath;
    
    private String fileName;
    
    @Column(nullable = false)
    private String status;
    
    private String finalDecision;
    
    @Column(columnDefinition = "TEXT")
    private String extractedData;
    
    @Column(columnDefinition = "TEXT")
    private String processingResult;
    
    private Integer retryCount = 0;
    
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
