package com.smartdocflow.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedDataDTO {
    private String jobId;
    private String ocrText;
    private Map<String, Object> extractedFields;
    private Double confidence;
    
    // Common fields
    private String documentNumber;
    private String issueDate;
    private BigDecimal amount;
    
    // Invoice specific
    private String invoiceNumber;
    private BigDecimal totalAmount;
    private BigDecimal taxAmount;
    private String companyName;
    
    // Loan specific
    private BigDecimal loanAmount;
    private String applicantName;
    private String creditScore;
    private BigDecimal monthlyIncome;
    
    // KYC specific
    private String idNumber;
    private String nationality;
    private String address;
}
