package com.smartdocflow.worker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdocflow.worker.dto.WorkflowRuleDTO;
import com.smartdocflow.worker.engine.RuleEngine;
import com.smartdocflow.worker.entity.DocumentJob;
import com.smartdocflow.worker.repository.DocumentJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobProcessingService {

    private final WebClient.Builder webClientBuilder;
    private final RuleEngine ruleEngine;
    private final ActionExecutorService actionExecutorService;
    private final DocumentJobRepository jobRepository;

    @Value("${config.service.url}")
    private String configServiceUrl;

    @Value("${ai.service.url}")
    private String aiServiceUrl;


    @Transactional
    public void processJob(Map<String, Object> jobData) {
        String jobId = (String) jobData.get("jobId");
        long startTime = System.currentTimeMillis();

        log.info("Processing job: {}", jobId);

        try {
            updateJobStatus(jobId, "PROCESSING");

            // Step 1: Call AI Service for data extraction
            updateJobStatus(jobId, "AI_ENRICHMENT");
            Map<String, Object> extractedData = callAiService(jobData);

            // Step 2: Fetch applicable rules - NOW RETURNS POJO
            updateJobStatus(jobId, "RULE_EVALUATION");
            String documentType = (String) jobData.get("documentType");
            List<WorkflowRuleDTO> rules = fetchRules(documentType);

            if (rules.isEmpty()) {
                log.warn("No rules found for document type: {}", documentType);
                handleNoRulesScenario(jobId, extractedData);
                return;
            }

            // Step 3: Evaluate rules and get decision
            RuleEngine.RuleEvaluationResult result = evaluateRules(jobId, extractedData, rules);

            // Step 4: Execute actions based on decision
            updateJobStatus(jobId, "ACTION_EXECUTION");
            executeActions(jobId, result, extractedData);

            // Step 5: Save final result
            long processingTime = System.currentTimeMillis() - startTime;
            saveResult(jobId, extractedData, result, processingTime);

            log.info("Job {} completed with decision: {} in {}ms",
                    jobId, result.getDecision(), processingTime);

        } catch (Exception e) {
            log.error("Job processing failed: {}", jobId, e);
            markAsFailed(jobId, e.getMessage());
        }
    }

    /**
     * Fetches rules from config service - NOW RETURNS POJO
     */
    private List<WorkflowRuleDTO> fetchRules(String documentType) {
        log.info("Fetching rules for document type: {}", documentType);

        try {
            // Use ParameterizedTypeReference for proper generic type handling
            List<WorkflowRuleDTO> rules = webClientBuilder.build()
                    .get()
                    .uri(configServiceUrl + "/api/config/rules/" + documentType)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<WorkflowRuleDTO>>() {
                    })
                    .block();

            if (rules == null || rules.isEmpty()) {
                log.warn("No rules found for document type: {}", documentType);
                return new ArrayList<>();
            }

            // Sort by priority (lower number = higher priority) - Type-safe!
            rules.sort(Comparator.comparing(WorkflowRuleDTO::getPriority));

            // Filter only active rules - Clean and readable
            List<WorkflowRuleDTO> activeRules = rules.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getActive()))
                    .collect(Collectors.toList());

            log.info("Fetched {} active rules for {}", activeRules.size(), documentType);
            return activeRules;

        } catch (Exception e) {
            log.error("Failed to fetch rules for {}", documentType, e);
            return new ArrayList<>();
        }
    }

    /**
     * Evaluates rules in priority order - NOW USES POJO
     */
    private RuleEngine.RuleEvaluationResult evaluateRules(String jobId,
                                                          Map<String, Object> extractedData,
                                                          List<WorkflowRuleDTO> rules) {
        log.info("Evaluating {} rules for job: {}", rules.size(), jobId);

        // Prepare data for evaluation
        Map<String, Object> evaluationData = prepareEvaluationData(extractedData);

        // Evaluate rules in priority order
        for (WorkflowRuleDTO rule : rules) {
            log.debug("Evaluating rule: {} (Priority: {})",
                    rule.getRuleName(), rule.getPriority());

            RuleEngine.RuleEvaluationResult result = ruleEngine.evaluateRule(rule, evaluationData);

            if (result.isMatched()) {
                log.info("Rule matched: {} -> Decision: {}",
                        rule.getRuleName(), result.getDecision());
                return result;
            }
        }

        // No rules matched - default to manual review
        log.warn("No rules matched for job: {}", jobId);
        return RuleEngine.RuleEvaluationResult.builder()
                .matched(false)
                .ruleName("DEFAULT")
                .decision("MANUAL_REVIEW_REQUIRED")
                .reason("No matching rules found - requires manual review")
                .actions(Collections.emptyList())
                .build();
    }

    /**
     * Prepares data for rule evaluation by flattening nested structures
     */
    private Map<String, Object> prepareEvaluationData(Map<String, Object> extractedData) {
        Map<String, Object> evaluationData = new HashMap<>();

        // Add all top-level fields first
        evaluationData.putAll(extractedData);

        // Add nested extractedFields if present and flatten them
        @SuppressWarnings("unchecked")
        Map<String, Object> extractedFields =
                (Map<String, Object>) extractedData.get("extractedFields");

        if (extractedFields != null) {
            // Add all nested fields at the root level for easy access
            evaluationData.putAll(extractedFields);
        }

        // Add metadata if present
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata =
                (Map<String, Object>) extractedData.get("metadata");

        if (metadata != null) {
            evaluationData.putAll(metadata);
        }

        log.debug("Prepared evaluation data with {} fields: {}",
                evaluationData.size(), evaluationData.keySet());

        return evaluationData;
    }

    /**
     * Executes actions based on rule evaluation result - NOW USES POJO
     */
    private void executeActions(String jobId,
                                RuleEngine.RuleEvaluationResult result,
                                Map<String, Object> extractedData) {
        if (result.getActions() == null || result.getActions().isEmpty()) {
            log.info("No actions to execute for job: {}", jobId);
            return;
        }

        log.info("Executing {} actions for job: {}", result.getActions().size(), jobId);

        Map<String, Object> actionContext = new HashMap<>();
        actionContext.put("jobId", jobId);
        actionContext.put("decision", result.getDecision());
        actionContext.put("reason", result.getReason());
        actionContext.put("ruleName", result.getRuleName());
        actionContext.putAll(extractedData);

        // Now accepts WorkflowRuleDTO.DecisionAction.ActionItem
        for (WorkflowRuleDTO.DecisionAction.ActionItem action : result.getActions()) {
            try {
                actionExecutorService.executeAction(action, actionContext);
            } catch (Exception e) {
                log.error("Failed to execute action: {}", action.getActionType(), e);
                // Continue with other actions even if one fails
            }
        }
    }

    /**
     * Handles scenario when no rules are configured
     */
    private void handleNoRulesScenario(String jobId, Map<String, Object> extractedData) {
        log.warn("No rules configured - defaulting to manual review for job: {}", jobId);

        RuleEngine.RuleEvaluationResult result = RuleEngine.RuleEvaluationResult.builder()
                .matched(false)
                .ruleName("NO_RULES")
                .decision("MANUAL_REVIEW_REQUIRED")
                .reason("No rules configured for this document type")
                .actions(Collections.emptyList())
                .build();

        saveResult(jobId, extractedData, result, 0L);
    }

    /**
     * Calls AI service to extract data from document
     */
    private Map<String, Object> callAiService(Map<String, Object> jobData) {
        log.info("Calling AI service for job: {}", jobData.get("jobId"));
        // Implementation depends on your AI service API
        return webClientBuilder.build()
                .post()
                .uri(aiServiceUrl + "/api/ai/extract?jobId={jobId}&filePath={filePath}&documentType={documentType}",
                        jobData.get("jobId"), jobData.get("filePath"), jobData.get("documentType"))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    /**
     * Updates job status
     */
    private void updateJobStatus(String jobId, String status) {
        log.debug("Job {} status: {}", jobId, status);
        // Implementation to update job status in database
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            jobRepository.save(job);
        });
    }

    /**
     * Saves processing result
     */
    @Transactional
    private void saveResult(String jobId,
                            Map<String, Object> extractedData,
                            RuleEngine.RuleEvaluationResult result,
                            Long processingTime) {

        log.info("Saving processing result for jobId={}", jobId);

        DocumentJob job = jobRepository.findById(jobId)
                .orElseThrow(() ->
                        new IllegalStateException("Job not found: " + jobId));

        try {
            ObjectMapper mapper = new ObjectMapper();

            job.setExtractedData(mapper.writeValueAsString(extractedData));
            job.setFinalDecision(result.getDecision());
            job.setProcessingResult(result.getReason());
            job.setProcessingTimeMs(processingTime);
            job.setStatus("COMPLETED");
            job.setCompletedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());

            jobRepository.save(job);

            log.info("Job {} updated successfully with decision={}",
                    jobId, result.getDecision());

        } catch (Exception e) {
            log.error("Failed to save result for jobId={}", jobId, e);
            throw new RuntimeException("Failed to update job result", e);
        }
    }


    /**
     * Marks job as failed
     */
    private void markAsFailed(String jobId, String errorMessage) {
        log.error("Marking job {} as failed: {}", jobId, errorMessage);
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus("FAILED");
            job.setErrorMessage(errorMessage);
            job.setRetryCount(job.getRetryCount() + 1);
            jobRepository.save(job);
        });
    }
}