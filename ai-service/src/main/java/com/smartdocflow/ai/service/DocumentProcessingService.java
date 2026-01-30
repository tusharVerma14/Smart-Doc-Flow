package com.smartdocflow.ai.service;

import com.smartdocflow.ai.dto.ExtractedDataDTO;
import com.smartdocflow.ai.extractor.InvoiceExtractor;
import com.smartdocflow.ai.extractor.KycExtractor;
import com.smartdocflow.ai.extractor.LoanExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingService {

    private final OcrService ocrService;
    private final MinioService minioService;  // ← Add this
    private final InvoiceExtractor invoiceExtractor;
    private final LoanExtractor loanExtractor;
    private final KycExtractor kycExtractor;

    public ExtractedDataDTO processDocument(String jobId, String filePath, String documentType) {
        log.info("Processing document: jobId={}, type={}, path={}", jobId, documentType, filePath);

        File tempFile = null;
        try {
            // Step 1: Download from MinIO
            tempFile = minioService.downloadFileToTemp(filePath);
            log.info("File downloaded from MinIO: {}", tempFile.getAbsolutePath());

            // Step 2: OCR
            String ocrText = ocrService.extractText(tempFile);
            log.info("OCR completed: {} characters extracted", ocrText.length());

            // Step 3: Extract structured data based on document type
            ExtractedDataDTO result = switch (documentType.toUpperCase()) {
                case "INVOICE" -> invoiceExtractor.extract(ocrText, jobId);
                case "LOAN_APPLICATION" -> loanExtractor.extract(ocrText, jobId);
                case "KYC" -> kycExtractor.extract(ocrText, jobId);
                default -> {
                    log.warn("No specific extractor for type: {}, returning OCR only", documentType);
                    yield ExtractedDataDTO.builder()
                            .jobId(jobId)
                            .ocrText(ocrText)
                            .confidence(0.5)
                            .build();
                }
            };

            log.info("Extraction completed: confidence={}", result.getConfidence());
            return result;

        } finally {
            // Step 4: Cleanup temp file
            if (tempFile != null) {
                minioService.deleteTempFile(tempFile);
            }
        }
    }
}