package com.smartdocflow.ai.extractor;

import com.smartdocflow.ai.dto.ExtractedDataDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Universal Invoice Extractor
 * Supports: Flipkart, Amazon, Uber, Swiggy, Zomato, GST Invoices, B2B Invoices, etc.
 */
@Component
@Slf4j
public class InvoiceExtractor {

    // INVOICE NUMBER PATTERNS (Priority Order)
    private static final List<Pattern> INVOICE_NUMBER_PATTERNS = Arrays.asList(
            Pattern.compile("Invoice\\s+Number\\s*#?\\s*[:#]?\\s*([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Invoice\\s*#\\s*:?\\s*([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Tax\\s+Invoice\\s*#?\\s*:?\\s*([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Bill\\s+Number\\s*:?\\s*([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Order\\s+ID\\s*:?\\s*([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Receipt\\s+(?:No|Number)\\s*:?\\s*([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Bill\\s+of\\s+Supply\\s+Number\\s*:?\\s*([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE)
    );

    // DATE PATTERNS
    private static final List<Pattern> DATE_PATTERNS = Arrays.asList(
            Pattern.compile("Invoice\\s+Date\\s*:?\\s*(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Bill\\s+Date\\s*:?\\s*(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Date\\s+of\\s+Issue\\s*:?\\s*(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Date\\s*:?\\s*(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})", Pattern.CASE_INSENSITIVE)
    );

    // AMOUNT PATTERNS
    private static final List<Pattern> TOTAL_PATTERNS = Arrays.asList(
            Pattern.compile("Grand\\s+Total\\s*[₹=:]*\\s*₹?\\s*([0-9,]+\\.\\d{2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Net\\s+Total\\s*[₹=:]*\\s*₹?\\s*([0-9,]+\\.\\d{2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Total\\s+Amount\\s*[₹=:]*\\s*₹?\\s*([0-9,]+\\.\\d{2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Total\\s*₹\\s*([0-9,]+\\.\\d{2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Total\\s*:\\s*\\$\\s*([0-9,]+\\.\\d{2})", Pattern.CASE_INSENSITIVE)
    );

    // TAX PATTERNS
    private static final List<Pattern> TAX_PATTERNS = Arrays.asList(
            Pattern.compile("IGST\\s*[₹:]*\\s*₹?\\s*([0-9,]+\\.\\d{2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("CGST\\s*[₹:]*\\s*₹?\\s*([0-9,]+\\.\\d{2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("SGST\\s*[₹:]*\\s*₹?\\s*([0-9,]+\\.\\d{2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("GST\\s*[₹:]*\\s*₹?\\s*([0-9,]+\\.\\d{2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("VAT\\s*[₹$:]*\\s*[₹$]?\\s*([0-9,]+\\.\\d{2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Tax\\s*[₹$:]*\\s*[₹$]?\\s*([0-9,]+\\.\\d{2})", Pattern.CASE_INSENSITIVE)
    );

    // COMPANY PATTERNS
    private static final List<Pattern> COMPANY_PATTERNS = Arrays.asList(
            Pattern.compile("Sold\\s+By\\s*:?\\s*([A-Za-z0-9\\s&.,()]+?)(?:,|\\n)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Seller\\s*:?\\s*([A-Za-z0-9\\s&.,()]+?)(?:,|\\n)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Vendor\\s*:?\\s*([A-Za-z0-9\\s&.,()]+?)(?:,|\\n)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Company\\s*:?\\s*([A-Za-z0-9\\s&.,()]+?)(?:,|\\n)", Pattern.CASE_INSENSITIVE)
    );

    // GSTIN PATTERN
    private static final Pattern GSTIN_PATTERN = Pattern.compile(
            "GSTIN\\s*[-:]*\\s*([0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1})",
            Pattern.CASE_INSENSITIVE
    );

    public ExtractedDataDTO extract(String ocrText, String jobId) {
        log.info("🔍 Extracting invoice data from OCR text ({} chars)", ocrText.length());

        Map<String, Object> fields = new HashMap<>();

        // Extract all fields
        List<String> invoiceNums = extractMultiple(INVOICE_NUMBER_PATTERNS, ocrText);
        String invoiceNum = selectBestInvoiceNumber(invoiceNums);

        List<String> dates = extractMultiple(DATE_PATTERNS, ocrText);
        String date = dates.isEmpty() ? null : dates.get(0);

        List<BigDecimal> totals = extractAmounts(TOTAL_PATTERNS, ocrText);
        BigDecimal total = selectBestTotal(totals);

        List<BigDecimal> taxes = extractAmounts(TAX_PATTERNS, ocrText);
        BigDecimal tax = sumTaxes(taxes);

        List<String> companies = extractMultiple(COMPANY_PATTERNS, ocrText);
        String company = companies.isEmpty() ? null : cleanCompanyName(companies.get(0));

        String gstin = extractField(GSTIN_PATTERN, ocrText, 1);

        // Calculate subtotal
        BigDecimal subtotal = null;
        if (total != null && tax != null) {
            subtotal = total.subtract(tax);
        }

        fields.put("invoiceNumber", invoiceNum);
        fields.put("date", date);
        fields.put("total", total);
        fields.put("subtotal", subtotal);
        fields.put("tax", tax);
        fields.put("company", company);
        fields.put("gstin", gstin);

        log.info("✅ Extracted - Invoice: {}, Date: {}, Total: ₹{}, Tax: ₹{}, Company: {}",
                invoiceNum, date, total, tax, company);

        double confidence = calculateConfidence(fields, ocrText);

        return ExtractedDataDTO.builder()
                .jobId(jobId)
                .ocrText(ocrText)
                .extractedFields(fields)
                .confidence(confidence)
                .invoiceNumber(invoiceNum)
                .issueDate(date)
                .totalAmount(total)
                .taxAmount(tax)
                .companyName(company)
                .build();
    }

    private List<String> extractMultiple(List<Pattern> patterns, String text) {
        List<String> results = new ArrayList<>();
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String match = matcher.group(1).trim();
                if (!match.isEmpty() && !results.contains(match)) {
                    results.add(match);
                }
            }
        }
        return results;
    }

    private String extractField(Pattern pattern, String text, int group) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(group).trim() : null;
    }

    private List<BigDecimal> extractAmounts(List<Pattern> patterns, String text) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                try {
                    String amountStr = matcher.group(1)
                            .replace(",", "")
                            .replace("₹", "")
                            .replace("$", "")
                            .trim();
                    BigDecimal amount = new BigDecimal(amountStr);
                    amounts.add(amount);
                } catch (NumberFormatException e) {
                    log.warn("⚠️  Failed to parse amount: {}", matcher.group(1));
                }
            }
        }
        return amounts;
    }

    private String selectBestInvoiceNumber(List<String> invoiceNums) {
        if (invoiceNums.isEmpty()) return null;

        List<String> filtered = invoiceNums.stream()
                .filter(num -> !num.equalsIgnoreCase("Sold"))
                .filter(num -> !num.equalsIgnoreCase("By"))
                .filter(num -> num.length() >= 5)
                .toList();

        if (filtered.isEmpty()) return invoiceNums.get(0);

        return filtered.stream()
                .max(Comparator.comparingInt(String::length))
                .orElse(filtered.get(0));
    }

    private BigDecimal selectBestTotal(List<BigDecimal> totals) {
        return totals.isEmpty() ? null : totals.stream()
                .max(BigDecimal::compareTo)
                .orElse(null);
    }

    private BigDecimal sumTaxes(List<BigDecimal> taxes) {
        if (taxes.isEmpty()) return null;
        BigDecimal sum = taxes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.compareTo(BigDecimal.ZERO) > 0 ? sum : null;
    }

    private String cleanCompanyName(String company) {
        return company == null ? null : company
                .replaceAll("\\s+", " ")
                .replaceAll(",$", "")
                .trim();
    }

    private double calculateConfidence(Map<String, Object> fields, String ocrText) {
        int score = 0;
        int maxScore = 7;

        if (fields.get("invoiceNumber") != null) score += 2;
        if (fields.get("date") != null) score += 2;
        if (fields.get("total") != null) score += 2;
        if (fields.get("company") != null) score += 1;
        if (fields.get("tax") != null) score += 1;
        if (fields.get("gstin") != null) score += 1;

        if (ocrText.length() < 200) score = Math.max(0, score - 2);

        return Math.min(1.0, (double) score / maxScore);
    }
}