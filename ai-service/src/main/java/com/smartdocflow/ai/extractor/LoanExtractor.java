package com.smartdocflow.ai.extractor;

import com.smartdocflow.ai.dto.ExtractedDataDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Universal Loan Application Extractor
 * Supports: Personal Loan, Home Loan, Auto Loan, Business Loan
 */
@Component
@Slf4j
public class LoanExtractor {

    // ========================================
    // LOAN APPLICATION NUMBER PATTERNS
    // ========================================
    private static final List<Pattern> APPLICATION_NUMBER_PATTERNS = Arrays.asList(
            Pattern.compile("Application\\s+(?:No|Number|ID)\\s*:?\\s*([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Loan\\s+(?:No|Number|ID)\\s*:?\\s*([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Reference\\s+(?:No|Number)\\s*:?\\s*([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("File\\s+Number\\s*:?\\s*([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE)
    );

    // ========================================
    // APPLICANT NAME PATTERNS
    // ========================================
    private static final List<Pattern> APPLICANT_NAME_PATTERNS = Arrays.asList(
            Pattern.compile("Applicant\\s+Name\\s*:?\\s*([A-Za-z\\s]{3,50})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Borrower\\s+Name\\s*:?\\s*([A-Za-z\\s]{3,50})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Customer\\s+Name\\s*:?\\s*([A-Za-z\\s]{3,50})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Name\\s+of\\s+Applicant\\s*:?\\s*([A-Za-z\\s]{3,50})", Pattern.CASE_INSENSITIVE)
    );

    // ========================================
    // LOAN AMOUNT PATTERNS
    // ========================================
    private static final List<Pattern> LOAN_AMOUNT_PATTERNS = Arrays.asList(
            Pattern.compile("Loan\\s+Amount\\s*[₹:]*\\s*₹?\\s*([0-9,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Amount\\s+Requested\\s*[₹:]*\\s*₹?\\s*([0-9,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Principal\\s+Amount\\s*[₹:]*\\s*₹?\\s*([0-9,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Sanctioned\\s+Amount\\s*[₹:]*\\s*₹?\\s*([0-9,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Disbursement\\s+Amount\\s*[₹:]*\\s*₹?\\s*([0-9,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE)
    );

    // ========================================
    // MONTHLY INCOME PATTERNS
    // ========================================
    private static final List<Pattern> INCOME_PATTERNS = Arrays.asList(
            Pattern.compile("Monthly\\s+Income\\s*[₹:]*\\s*₹?\\s*([0-9,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Gross\\s+Income\\s*[₹:]*\\s*₹?\\s*([0-9,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Net\\s+Income\\s*[₹:]*\\s*₹?\\s*([0-9,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Salary\\s*[₹:]*\\s*₹?\\s*([0-9,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Annual\\s+Income\\s*[₹:]*\\s*₹?\\s*([0-9,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE)
    );

    // ========================================
    // EMPLOYMENT/OCCUPATION PATTERNS
    // ========================================
    private static final List<Pattern> EMPLOYMENT_PATTERNS = Arrays.asList(
            Pattern.compile("Occupation\\s*:?\\s*([A-Za-z\\s]{3,40})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Employment\\s+Type\\s*:?\\s*([A-Za-z\\s]{3,40})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Profession\\s*:?\\s*([A-Za-z\\s]{3,40})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Job\\s+Title\\s*:?\\s*([A-Za-z\\s]{3,40})", Pattern.CASE_INSENSITIVE)
    );

    // ========================================
    // CREDIT SCORE PATTERNS
    // ========================================
    private static final List<Pattern> CREDIT_SCORE_PATTERNS = Arrays.asList(
            Pattern.compile("Credit\\s+Score\\s*:?\\s*(\\d{3})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("CIBIL\\s+Score\\s*:?\\s*(\\d{3})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("FICO\\s+Score\\s*:?\\s*(\\d{3})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Score\\s*:?\\s*(\\d{3})", Pattern.CASE_INSENSITIVE)
    );

    // ========================================
    // LOAN TENURE PATTERNS
    // ========================================
    private static final List<Pattern> TENURE_PATTERNS = Arrays.asList(
            Pattern.compile("Tenure\\s*:?\\s*(\\d+)\\s*(?:months?|yrs?|years?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Loan\\s+Period\\s*:?\\s*(\\d+)\\s*(?:months?|yrs?|years?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Repayment\\s+Period\\s*:?\\s*(\\d+)\\s*(?:months?|yrs?|years?)", Pattern.CASE_INSENSITIVE)
    );

    // ========================================
    // INTEREST RATE PATTERNS
    // ========================================
    private static final List<Pattern> INTEREST_RATE_PATTERNS = Arrays.asList(
            Pattern.compile("Interest\\s+Rate\\s*:?\\s*(\\d+(?:\\.\\d{1,2})?)\\s*%", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ROI\\s*:?\\s*(\\d+(?:\\.\\d{1,2})?)\\s*%", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Rate\\s+of\\s+Interest\\s*:?\\s*(\\d+(?:\\.\\d{1,2})?)\\s*%", Pattern.CASE_INSENSITIVE)
    );

    // ========================================
    // LOAN TYPE PATTERNS
    // ========================================
    private static final Pattern LOAN_TYPE_PATTERN = Pattern.compile(
            "(Personal\\s+Loan|Home\\s+Loan|Auto\\s+Loan|Car\\s+Loan|Business\\s+Loan|Education\\s+Loan|Gold\\s+Loan)",
            Pattern.CASE_INSENSITIVE
    );

    // ========================================
    // PAN PATTERN
    // ========================================
    private static final Pattern PAN_PATTERN = Pattern.compile(
            "PAN\\s*:?\\s*([A-Z]{5}[0-9]{4}[A-Z]{1})",
            Pattern.CASE_INSENSITIVE
    );

    public ExtractedDataDTO extract(String ocrText, String jobId) {
        log.info("🏦 Extracting loan application data from OCR text ({} chars)", ocrText.length());

        Map<String, Object> fields = new HashMap<>();

        // Extract all fields
        String applicationNumber = extractFirst(APPLICATION_NUMBER_PATTERNS, ocrText);
        String applicantName = extractFirst(APPLICANT_NAME_PATTERNS, ocrText);

        BigDecimal loanAmount = extractMaxAmount(LOAN_AMOUNT_PATTERNS, ocrText);
        BigDecimal monthlyIncome = extractMaxAmount(INCOME_PATTERNS, ocrText);

        String employment = extractFirst(EMPLOYMENT_PATTERNS, ocrText);
        String creditScore = extractFirst(CREDIT_SCORE_PATTERNS, ocrText);
        String tenure = extractFirst(TENURE_PATTERNS, ocrText);
        String interestRate = extractFirst(INTEREST_RATE_PATTERNS, ocrText);

        String loanType = extractField(LOAN_TYPE_PATTERN, ocrText, 1);
        String panNumber = extractField(PAN_PATTERN, ocrText, 1);

        // Clean data
        applicantName = cleanName(applicantName);
        employment = cleanText(employment);
        loanType = cleanText(loanType);

        // Calculate debt-to-income ratio if possible
        Double dtiRatio = null;
        if (loanAmount != null && monthlyIncome != null && monthlyIncome.compareTo(BigDecimal.ZERO) > 0) {
            // Assuming 20-year tenure as default for calculation
            BigDecimal emi = calculateEMI(loanAmount, interestRate != null ? new BigDecimal(interestRate) : new BigDecimal("10.5"), 240);
            dtiRatio = emi.divide(monthlyIncome, 4, BigDecimal.ROUND_HALF_UP).doubleValue() * 100;
        }

        // Populate fields
        fields.put("applicationNumber", applicationNumber);
        fields.put("applicantName", applicantName);
        fields.put("loanAmount", loanAmount);
        fields.put("monthlyIncome", monthlyIncome);
        fields.put("employment", employment);
        fields.put("creditScore", creditScore);
        fields.put("tenure", tenure);
        fields.put("interestRate", interestRate);
        fields.put("loanType", loanType);
        fields.put("panNumber", panNumber);
        fields.put("debtToIncomeRatio", dtiRatio);

        log.info("✅ Extracted - App#: {}, Name: {}, Loan: ₹{}, Income: ₹{}, Credit: {}",
                applicationNumber, applicantName, loanAmount, monthlyIncome, creditScore);

        double confidence = calculateConfidence(fields, ocrText);

        return ExtractedDataDTO.builder()
                .jobId(jobId)
                .ocrText(ocrText)
                .extractedFields(fields)
                .confidence(confidence)
                .loanAmount(loanAmount)
                .applicantName(applicantName)
                .creditScore(creditScore)
                .monthlyIncome(monthlyIncome)
                .build();
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
     * Extract maximum amount from multiple patterns
     */
    private BigDecimal extractMaxAmount(List<Pattern> patterns, String text) {
        BigDecimal max = null;

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                try {
                    String amountStr = matcher.group(1)
                            .replace(",", "")
                            .replace("₹", "")
                            .trim();
                    BigDecimal amount = new BigDecimal(amountStr);

                    if (max == null || amount.compareTo(max) > 0) {
                        max = amount;
                    }
                } catch (NumberFormatException e) {
                    log.warn("⚠️  Failed to parse amount: {}", matcher.group(1));
                }
            }
        }

        return max;
    }

    /**
     * Clean name
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
     * Clean generic text
     */
    private String cleanText(String text) {
        if (text == null) return null;

        return text
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Calculate EMI (Equated Monthly Installment)
     */
    private BigDecimal calculateEMI(BigDecimal principal, BigDecimal annualRate, int tenureMonths) {
        if (principal == null || annualRate == null) return BigDecimal.ZERO;

        // Monthly interest rate
        BigDecimal monthlyRate = annualRate.divide(new BigDecimal("1200"), 10, BigDecimal.ROUND_HALF_UP);

        // EMI = [P x R x (1+R)^N] / [(1+R)^N-1]
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal onePlusRPowN = onePlusR.pow(tenureMonths);

        BigDecimal numerator = principal.multiply(monthlyRate).multiply(onePlusRPowN);
        BigDecimal denominator = onePlusRPowN.subtract(BigDecimal.ONE);

        return numerator.divide(denominator, 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Calculate confidence score
     */
    private double calculateConfidence(Map<String, Object> fields, String ocrText) {
        int score = 0;
        int maxScore = 8;

        // Critical fields
        if (fields.get("applicantName") != null) score += 2;
        if (fields.get("loanAmount") != null) score += 2;
        if (fields.get("applicationNumber") != null) score += 1;

        // Important fields
        if (fields.get("monthlyIncome") != null) score += 1;
        if (fields.get("creditScore") != null) score += 1;
        if (fields.get("employment") != null) score += 1;
        if (fields.get("panNumber") != null) score += 1;
        if (fields.get("loanType") != null) score += 1;

        // Penalty for short text
        if (ocrText.length() < 200) score = Math.max(0, score - 2);

        return Math.min(1.0, (double) score / maxScore);
    }
}