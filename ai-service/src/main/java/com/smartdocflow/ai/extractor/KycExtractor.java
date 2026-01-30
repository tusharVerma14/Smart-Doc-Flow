package com.smartdocflow.ai.extractor;

import com.smartdocflow.ai.dto.ExtractedDataDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Universal KYC Document Extractor
 * Supports: Aadhaar, PAN Card, Passport, Driving License, Voter ID
 */
@Component
@Slf4j
public class KycExtractor {

    // ========================================
    // AADHAAR CARD PATTERNS
    // ========================================
    private static final List<Pattern> AADHAAR_PATTERNS = Arrays.asList(
            // Standard Aadhaar format: 1234 5678 9012
            Pattern.compile("(\\d{4}\\s\\d{4}\\s\\d{4})"),
            // Without spaces
            Pattern.compile("Aadhaar\\s*(?:No|Number)?\\s*:?\\s*(\\d{12})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("UID\\s*:?\\s*(\\d{12})", Pattern.CASE_INSENSITIVE)
    );

    // ========================================
    // PAN CARD PATTERNS
    // ========================================
    private static final List<Pattern> PAN_PATTERNS = Arrays.asList(
            // PAN format: ABCDE1234F
            Pattern.compile("PAN\\s*(?:No|Number)?\\s*:?\\s*([A-Z]{5}[0-9]{4}[A-Z]{1})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b([A-Z]{5}[0-9]{4}[A-Z]{1})\\b"),
            Pattern.compile("Permanent\\s+Account\\s+Number\\s*:?\\s*([A-Z]{5}[0-9]{4}[A-Z]{1})", Pattern.CASE_INSENSITIVE)
    );

    // ========================================
    // PASSPORT PATTERNS
    // ========================================
    private static final List<Pattern> PASSPORT_PATTERNS = Arrays.asList(
            // Indian Passport: A1234567
            Pattern.compile("Passport\\s*(?:No|Number)?\\s*:?\\s*([A-Z]\\d{7})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b([A-Z]\\d{7})\\b"),
            // International format
            Pattern.compile("Passport\\s*:?\\s*([A-Z0-9]{6,9})", Pattern.CASE_INSENSITIVE)
    );

    // ========================================
    // DRIVING LICENSE PATTERNS
    // ========================================
    private static final List<Pattern> DL_PATTERNS = Arrays.asList(
            // Format varies by state: DL-1234567890123, MH01 20150012345
            Pattern.compile("DL\\s*(?:No|Number)?\\s*[:-]?\\s*([A-Z]{2}[-\\s]?\\d{2}[-\\s]?\\d{11})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Driving\\s+Licen[cs]e\\s*:?\\s*([A-Z0-9\\s-]{10,20})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("License\\s*No\\s*:?\\s*([A-Z0-9\\s-]{10,20})", Pattern.CASE_INSENSITIVE)
    );

    // ========================================
    // VOTER ID PATTERNS
    // ========================================
    private static final List<Pattern> VOTER_ID_PATTERNS = Arrays.asList(
            // Format: ABC1234567
            Pattern.compile("Voter\\s*(?:ID|Card)?\\s*:?\\s*([A-Z]{3}\\d{7})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("EPIC\\s*(?:No)?\\s*:?\\s*([A-Z]{3}\\d{7})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b([A-Z]{3}\\d{7})\\b")
    );

    // ========================================
    // NAME PATTERNS
    // ========================================
    private static final List<Pattern> NAME_PATTERNS = Arrays.asList(
            Pattern.compile("Name\\s*:?\\s*([A-Za-z\\s]{3,50})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Full\\s+Name\\s*:?\\s*([A-Za-z\\s]{3,50})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Applicant\\s+Name\\s*:?\\s*([A-Za-z\\s]{3,50})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Cardholder\\s+Name\\s*:?\\s*([A-Za-z\\s]{3,50})", Pattern.CASE_INSENSITIVE)
    );

    // ========================================
    // DATE OF BIRTH PATTERNS
    // ========================================
    private static final List<Pattern> DOB_PATTERNS = Arrays.asList(
            Pattern.compile("DOB\\s*:?\\s*(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Date\\s+of\\s+Birth\\s*:?\\s*(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Birth\\s*:?\\s*(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})", Pattern.CASE_INSENSITIVE)
    );

    // ========================================
    // GENDER PATTERNS
    // ========================================
    private static final Pattern GENDER_PATTERN = Pattern.compile(
            "(?:Gender|Sex)\\s*:?\\s*(Male|Female|M|F|Transgender|Other)",
            Pattern.CASE_INSENSITIVE
    );

    // ========================================
    // ADDRESS PATTERNS
    // ========================================
    private static final List<Pattern> ADDRESS_PATTERNS = Arrays.asList(
            Pattern.compile("Address\\s*:?\\s*([A-Za-z0-9,\\s-]{20,200})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Permanent\\s+Address\\s*:?\\s*([A-Za-z0-9,\\s-]{20,200})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Residential\\s+Address\\s*:?\\s*([A-Za-z0-9,\\s-]{20,200})", Pattern.CASE_INSENSITIVE)
    );

    // ========================================
    // NATIONALITY PATTERNS
    // ========================================
    private static final Pattern NATIONALITY_PATTERN = Pattern.compile(
            "Nationality\\s*:?\\s*(Indian|India|[A-Z][a-z]+)",
            Pattern.CASE_INSENSITIVE
    );

    public ExtractedDataDTO extract(String ocrText, String jobId) {
        log.info("🪪 Extracting KYC data from OCR text ({} chars)", ocrText.length());

        Map<String, Object> fields = new HashMap<>();

        // Detect document type
        String docType = detectDocumentType(ocrText);
        log.info("📄 Detected KYC document type: {}", docType);

        // Extract ID number based on type
        String idNumber = extractIdNumber(ocrText, docType);

        // Extract common fields
        String name = extractFirst(NAME_PATTERNS, ocrText);
        String dob = extractFirst(DOB_PATTERNS, ocrText);
        String gender = extractField(GENDER_PATTERN, ocrText, 1);
        String address = extractFirst(ADDRESS_PATTERNS, ocrText);
        String nationality = extractField(NATIONALITY_PATTERN, ocrText, 1);

        // Clean extracted data
        name = cleanName(name);
        address = cleanAddress(address);
        gender = normalizeGender(gender);

        // Populate fields
        fields.put("documentType", docType);
        fields.put("idNumber", idNumber);
        fields.put("name", name);
        fields.put("dateOfBirth", dob);
        fields.put("gender", gender);
        fields.put("address", address);
        fields.put("nationality", nationality);

        log.info("✅ Extracted - Type: {}, ID: {}, Name: {}, DOB: {}",
                docType, maskSensitiveData(idNumber), name, dob);

        double confidence = calculateConfidence(fields, docType, ocrText);

        return ExtractedDataDTO.builder()
                .jobId(jobId)
                .ocrText(ocrText)
                .extractedFields(fields)
                .confidence(confidence)
                .idNumber(idNumber)
                .nationality(nationality)
                .address(address)
                .build();
    }

    /**
     * Detect KYC document type
     */
    private String detectDocumentType(String text) {
        String textLower = text.toLowerCase();

        if (textLower.contains("aadhaar") || textLower.contains("aadhar") || textLower.contains("uid")) {
            return "AADHAAR";
        } else if (textLower.contains("permanent account number") || textLower.contains("income tax")) {
            return "PAN";
        } else if (textLower.contains("passport")) {
            return "PASSPORT";
        } else if (textLower.contains("driving") && textLower.contains("license")) {
            return "DRIVING_LICENSE";
        } else if (textLower.contains("voter") || textLower.contains("epic")) {
            return "VOTER_ID";
        }

        return "UNKNOWN";
    }

    /**
     * Extract ID number based on document type
     */
    private String extractIdNumber(String text, String docType) {
        return switch (docType) {
            case "AADHAAR" -> extractFirst(AADHAAR_PATTERNS, text);
            case "PAN" -> extractFirst(PAN_PATTERNS, text);
            case "PASSPORT" -> extractFirst(PASSPORT_PATTERNS, text);
            case "DRIVING_LICENSE" -> extractFirst(DL_PATTERNS, text);
            case "VOTER_ID" -> extractFirst(VOTER_ID_PATTERNS, text);
            default -> null;
        };
    }

    /**
     * Extract first match from multiple patterns
     */
    private String extractFirst(List<Pattern> patterns, String text) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    /**
     * Extract single field
     */
    private String extractField(Pattern pattern, String text, int group) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(group).trim() : null;
    }

    /**
     * Clean name (remove extra spaces, special chars)
     */
    private String cleanName(String name) {
        if (name == null) return null;

        return name
                .replaceAll("\\s+", " ")
                .replaceAll("[^A-Za-z\\s]", "")
                .trim()
                .toUpperCase();
    }

    /**
     * Clean address
     */
    private String cleanAddress(String address) {
        if (address == null) return null;

        return address
                .replaceAll("\\s+", " ")
                .replaceAll("\\n+", ", ")
                .trim();
    }

    /**
     * Normalize gender
     */
    private String normalizeGender(String gender) {
        if (gender == null) return null;

        String g = gender.toUpperCase();
        if (g.equals("M") || g.equals("MALE")) return "MALE";
        if (g.equals("F") || g.equals("FEMALE")) return "FEMALE";
        return gender.toUpperCase();
    }

    /**
     * Mask sensitive data for logging
     */
    private String maskSensitiveData(String data) {
        if (data == null || data.length() < 4) return "****";
        return data.substring(0, 4) + "****" + data.substring(Math.max(4, data.length() - 4));
    }

    /**
     * Calculate confidence score
     */
    private double calculateConfidence(Map<String, Object> fields, String docType, String ocrText) {
        int score = 0;
        int maxScore = 8;

        // Critical fields
        if (!"UNKNOWN".equals(docType)) score += 2;
        if (fields.get("idNumber") != null) score += 3;
        if (fields.get("name") != null) score += 2;

        // Optional fields
        if (fields.get("dateOfBirth") != null) score += 1;
        if (fields.get("address") != null) score += 1;
        if (fields.get("gender") != null) score += 1;

        // Penalty for short text
        if (ocrText.length() < 150) score = Math.max(0, score - 3);

        return Math.min(1.0, (double) score / maxScore);
    }
}