package com.smartdocflow.config.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdocflow.config.dto.WorkflowRuleDTO;
import com.smartdocflow.config.entity.WorkflowRule;
import com.smartdocflow.config.repository.WorkflowRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

    private final WorkflowRuleRepository ruleRepository;
    private final ObjectMapper objectMapper;

    public List<WorkflowRuleDTO> getRulesByDocumentType(String documentType) {
        log.info("Fetching rules for document type: {}", documentType);
        return ruleRepository.findByDocumentTypeAndActiveOrderByPriorityAsc(documentType, true)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<WorkflowRuleDTO> getAllActiveRules() {
        log.info("Fetching all active rules");
        return ruleRepository.findByActiveOrderByPriorityAsc(true)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public WorkflowRuleDTO getRuleById(Long id) {
        log.info("Fetching rule by ID: {}", id);
        return ruleRepository.findById(id)
                .map(this::toDTO)
                .orElse(null);
    }

    public List<String> getAllDocumentTypes() {
        log.info("Fetching all document types");
        return ruleRepository.findAll()
                .stream()
                .map(WorkflowRule::getDocumentType)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Transactional
    public WorkflowRuleDTO createRule(WorkflowRuleDTO ruleDTO) {
        log.info("Creating new rule: {}", ruleDTO.getRuleName());
        try {
            WorkflowRule entity = WorkflowRule.builder()
                    .ruleName(ruleDTO.getRuleName())
                    .ruleDescription(ruleDTO.getRuleDescription())
                    .documentType(ruleDTO.getDocumentType())
                    .priority(ruleDTO.getPriority())
                    .active(ruleDTO.getActive() != null ? ruleDTO.getActive() : true)
                    .conditions(serializeToJson(ruleDTO.getConditions()))
                    .conditionLogic(ruleDTO.getConditionLogic())
                    .customLogic(ruleDTO.getCustomLogic())
                    .requiredFields(serializeToJson(ruleDTO.getRequiredFields()))
                    .missingFieldAction(ruleDTO.getMissingFieldAction())
                    .autoApprovalAction(serializeToJson(ruleDTO.getAutoApprovalAction()))
                    .manualReviewAction(serializeToJson(ruleDTO.getManualReviewAction()))
                    .rejectionAction(serializeToJson(ruleDTO.getRejectionAction()))
                    .build();

            WorkflowRule saved = ruleRepository.save(entity);
            log.info("Rule created successfully with ID: {}", saved.getId());
            return toDTO(saved);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize rule data", e);
            throw new RuntimeException("Failed to create rule", e);
        }
    }

    @Transactional
    public WorkflowRuleDTO updateRule(Long id, WorkflowRuleDTO ruleDTO) {
        log.info("Updating rule ID: {}", id);
        WorkflowRule existing = ruleRepository.findById(id)
                .orElse(null);

        if (existing == null) {
            return null;
        }

        try {
            existing.setRuleName(ruleDTO.getRuleName());
            existing.setRuleDescription(ruleDTO.getRuleDescription());
            existing.setDocumentType(ruleDTO.getDocumentType());
            existing.setPriority(ruleDTO.getPriority());
            existing.setActive(ruleDTO.getActive());
            existing.setConditions(serializeToJson(ruleDTO.getConditions()));
            existing.setConditionLogic(ruleDTO.getConditionLogic());
            existing.setCustomLogic(ruleDTO.getCustomLogic());
            existing.setRequiredFields(serializeToJson(ruleDTO.getRequiredFields()));
            existing.setMissingFieldAction(ruleDTO.getMissingFieldAction());
            existing.setAutoApprovalAction(serializeToJson(ruleDTO.getAutoApprovalAction()));
            existing.setManualReviewAction(serializeToJson(ruleDTO.getManualReviewAction()));
            existing.setRejectionAction(serializeToJson(ruleDTO.getRejectionAction()));

            WorkflowRule saved = ruleRepository.save(existing);
            log.info("Rule updated successfully: {}", saved.getId());
            return toDTO(saved);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize rule data", e);
            throw new RuntimeException("Failed to update rule", e);
        }
    }

    @Transactional
    public boolean deleteRule(Long id) {
        log.info("Deleting rule ID: {}", id);
        if (!ruleRepository.existsById(id)) {
            return false;
        }
        ruleRepository.deleteById(id);
        log.info("Rule deleted successfully: {}", id);
        return true;
    }

    @Transactional
    public boolean updateRuleStatus(Long id, Boolean active) {
        log.info("Updating rule status ID: {} to active: {}", id, active);
        WorkflowRule existing = ruleRepository.findById(id).orElse(null);

        if (existing == null) {
            return false;
        }

        existing.setActive(active);
        ruleRepository.save(existing);
        log.info("Rule status updated successfully: {}", id);
        return true;
    }

    /**
     * Converts entity to DTO
     */
    private WorkflowRuleDTO toDTO(WorkflowRule entity) {
        try {
            return WorkflowRuleDTO.builder()
                    .id(entity.getId())
                    .ruleName(entity.getRuleName())
                    .ruleDescription(entity.getRuleDescription())
                    .documentType(entity.getDocumentType())
                    .priority(entity.getPriority())
                    .active(entity.getActive())
                    .conditions(deserializeList(entity.getConditions(),
                            new TypeReference<List<WorkflowRuleDTO.RuleCondition>>() {}))
                    .conditionLogic(entity.getConditionLogic())
                    .customLogic(entity.getCustomLogic())
                    .requiredFields(deserializeList(entity.getRequiredFields(),
                            new TypeReference<List<String>>() {}))
                    .missingFieldAction(entity.getMissingFieldAction())
                    .autoApprovalAction(deserializeObject(entity.getAutoApprovalAction(),
                            WorkflowRuleDTO.DecisionAction.class))
                    .manualReviewAction(deserializeObject(entity.getManualReviewAction(),
                            WorkflowRuleDTO.DecisionAction.class))
                    .rejectionAction(deserializeObject(entity.getRejectionAction(),
                            WorkflowRuleDTO.DecisionAction.class))
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize rule data for ID: {}", entity.getId(), e);
            throw new RuntimeException("Failed to convert rule to DTO", e);
        }
    }

    /**
     * Serializes object to JSON string
     */
    private String serializeToJson(Object obj) throws JsonProcessingException {
        if (obj == null) {
            return null;
        }
        return objectMapper.writeValueAsString(obj);
    }

    /**
     * Deserializes JSON string to list
     */
    private <T> T deserializeList(String json, TypeReference<T> typeReference)
            throws JsonProcessingException {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return objectMapper.readValue(json, typeReference);
    }

    /**
     * Deserializes JSON string to object
     */
    private <T> T deserializeObject(String json, Class<T> clazz)
            throws JsonProcessingException {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return objectMapper.readValue(json, clazz);
    }
}