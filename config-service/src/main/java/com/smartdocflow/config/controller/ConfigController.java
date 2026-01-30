package com.smartdocflow.config.controller;

import com.smartdocflow.config.dto.WorkflowRuleDTO;
import com.smartdocflow.config.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Validated
public class ConfigController {

    private final ConfigService configService;

    /**
     * Creates a new workflow rule
     */
    @PostMapping("/rules")
    public ResponseEntity<?> createRule(@Valid @RequestBody WorkflowRuleDTO ruleDTO) {
        log.info("POST /api/config/rules - Creating rule: {}", ruleDTO.getRuleName());

        try {
            // Validate rule configuration
            validateRuleConfiguration(ruleDTO);

            WorkflowRuleDTO created = configService.createRule(ruleDTO);

            log.info("Rule created successfully: {} (ID: {})",
                    created.getRuleName(), created.getId());

            return ResponseEntity.status(HttpStatus.CREATED).body(created);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid rule configuration: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", "INVALID_CONFIGURATION",
                            "message", e.getMessage()
                    ));

        } catch (Exception e) {
            log.error("Failed to create rule: {}", ruleDTO.getRuleName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "INTERNAL_ERROR",
                            "message", "Failed to create rule: " + e.getMessage()
                    ));
        }
    }

    /**
     * Gets all rules for a specific document type
     */
    @GetMapping("/rules/{documentType}")
    public ResponseEntity<?> getRulesByType(@PathVariable String documentType) {
        log.info("GET /api/config/rules/{}", documentType);

        try {
            List<WorkflowRuleDTO> rules = configService.getRulesByDocumentType(documentType);

            log.debug("Found {} rules for document type: {}", rules.size(), documentType);

            return ResponseEntity.ok(rules);

        } catch (Exception e) {
            log.error("Failed to fetch rules for document type: {}", documentType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "FETCH_ERROR",
                            "message", "Failed to fetch rules: " + e.getMessage()
                    ));
        }
    }

    /**
     * Gets a specific rule by ID
     */
    @GetMapping("/rules/id/{ruleId}")
    public ResponseEntity<?> getRuleById(@PathVariable Long ruleId) {
        log.info("GET /api/config/rules/id/{}", ruleId);

        try {
            WorkflowRuleDTO rule = configService.getRuleById(ruleId);

            if (rule == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(rule);

        } catch (Exception e) {
            log.error("Failed to fetch rule: {}", ruleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "FETCH_ERROR",
                            "message", "Failed to fetch rule: " + e.getMessage()
                    ));
        }
    }

    /**
     * Updates an existing rule
     */
    @PutMapping("/rules/{ruleId}")
    public ResponseEntity<?> updateRule(
            @PathVariable Long ruleId,
            @Valid @RequestBody WorkflowRuleDTO ruleDTO) {
        log.info("PUT /api/config/rules/{} - Updating rule", ruleId);

        try {
            validateRuleConfiguration(ruleDTO);

            ruleDTO.setId(ruleId);
            WorkflowRuleDTO updated = configService.updateRule(ruleId, ruleDTO);

            if (updated == null) {
                return ResponseEntity.notFound().build();
            }

            log.info("Rule updated successfully: {}", ruleId);
            return ResponseEntity.ok(updated);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid rule configuration: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", "INVALID_CONFIGURATION",
                            "message", e.getMessage()
                    ));

        } catch (Exception e) {
            log.error("Failed to update rule: {}", ruleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "UPDATE_ERROR",
                            "message", "Failed to update rule: " + e.getMessage()
                    ));
        }
    }

    /**
     * Deletes a rule
     */
    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<?> deleteRule(@PathVariable Long ruleId) {
        log.info("DELETE /api/config/rules/{}", ruleId);

        try {
            boolean deleted = configService.deleteRule(ruleId);

            if (!deleted) {
                return ResponseEntity.notFound().build();
            }

            log.info("Rule deleted successfully: {}", ruleId);
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.error("Failed to delete rule: {}", ruleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "DELETE_ERROR",
                            "message", "Failed to delete rule: " + e.getMessage()
                    ));
        }
    }

    /**
     * Activates or deactivates a rule
     */
    @PatchMapping("/rules/{ruleId}/status")
    public ResponseEntity<?> toggleRuleStatus(
            @PathVariable Long ruleId,
            @RequestParam Boolean active) {
        log.info("PATCH /api/config/rules/{}/status - Setting active: {}", ruleId, active);

        try {
            boolean updated = configService.updateRuleStatus(ruleId, active);

            if (!updated) {
                return ResponseEntity.notFound().build();
            }

            log.info("Rule status updated: {} -> active: {}", ruleId, active);
            return ResponseEntity.ok(Map.of(
                    "ruleId", ruleId,
                    "active", active,
                    "message", "Rule status updated successfully"
            ));

        } catch (Exception e) {
            log.error("Failed to update rule status: {}", ruleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "STATUS_UPDATE_ERROR",
                            "message", "Failed to update rule status: " + e.getMessage()
                    ));
        }
    }

    /**
     * Gets all document types that have rules configured
     */
    @GetMapping("/rules/document-types")
    public ResponseEntity<?> getAllDocumentTypes() {
        log.info("GET /api/config/rules/document-types");

        try {
            List<String> documentTypes = configService.getAllDocumentTypes();
            return ResponseEntity.ok(documentTypes);

        } catch (Exception e) {
            log.error("Failed to fetch document types", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "FETCH_ERROR",
                            "message", "Failed to fetch document types: " + e.getMessage()
                    ));
        }
    }

    /**
     * Validates rule configuration
     */
    private void validateRuleConfiguration(WorkflowRuleDTO ruleDTO) {
        // Validate basic fields
        if (ruleDTO.getRuleName() == null || ruleDTO.getRuleName().trim().isEmpty()) {
            throw new IllegalArgumentException("Rule name is required");
        }

        if (ruleDTO.getDocumentType() == null || ruleDTO.getDocumentType().trim().isEmpty()) {
            throw new IllegalArgumentException("Document type is required");
        }

        if (ruleDTO.getPriority() == null || ruleDTO.getPriority() < 1) {
            throw new IllegalArgumentException("Priority must be a positive number");
        }

        // Validate at least one action is configured
        if (ruleDTO.getAutoApprovalAction() == null &&
                ruleDTO.getManualReviewAction() == null &&
                ruleDTO.getRejectionAction() == null) {
            throw new IllegalArgumentException(
                    "At least one action (auto-approval, manual review, or rejection) must be configured");
        }

        // Validate conditions if present
        if (ruleDTO.getConditions() != null && !ruleDTO.getConditions().isEmpty()) {
            for (WorkflowRuleDTO.RuleCondition condition : ruleDTO.getConditions()) {
                validateCondition(condition);
            }

            // Validate custom logic if present
            if ("CUSTOM".equalsIgnoreCase(ruleDTO.getConditionLogic())) {
                if (ruleDTO.getCustomLogic() == null || ruleDTO.getCustomLogic().trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Custom logic expression is required when condition logic is CUSTOM");
                }
            }
        }

        // Validate actions
        validateAction(ruleDTO.getAutoApprovalAction(), "Auto Approval");
        validateAction(ruleDTO.getManualReviewAction(), "Manual Review");
        validateAction(ruleDTO.getRejectionAction(), "Rejection");
    }

    /**
     * Validates a single condition
     */
    private void validateCondition(WorkflowRuleDTO.RuleCondition condition) {
        if (condition.getField() == null || condition.getField().trim().isEmpty()) {
            throw new IllegalArgumentException("Condition field is required");
        }

        if (condition.getOperator() == null || condition.getOperator().trim().isEmpty()) {
            throw new IllegalArgumentException("Condition operator is required");
        }

        String operator = condition.getOperator().toUpperCase();

        // Validate value based on operator
        switch (operator) {
            case "RANGE":
                if (condition.getMinValue() == null || condition.getMaxValue() == null) {
                    throw new IllegalArgumentException(
                            "Both minValue and maxValue are required for RANGE operator");
                }
                break;

            case "IN":
            case "NOT_IN":
                if (condition.getValues() == null || condition.getValues().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Values list is required for " + operator + " operator");
                }
                break;

            case "REGEX":
                // For REGEX, value is optional (can have empty pattern)
                // but if provided, should be a valid regex
                if (condition.getValue() != null) {
                    try {
                        java.util.regex.Pattern.compile(condition.getValue().toString());
                    } catch (java.util.regex.PatternSyntaxException e) {
                        throw new IllegalArgumentException(
                                "Invalid regex pattern: " + e.getMessage());
                    }
                }
                break;

            default:
                if (condition.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Value is required for " + operator + " operator");
                }
        }
    }

    /**
     * Validates an action configuration
     * FIXED: Added proper type checking for actionConfig
     */
    private void validateAction(WorkflowRuleDTO.DecisionAction action, String actionName) {
        if (action == null) {
            return; // Action is optional
        }

        if (action.getActions() != null && !action.getActions().isEmpty()) {
            for (WorkflowRuleDTO.DecisionAction.ActionItem actionItem : action.getActions()) {
                if (actionItem.getActionType() == null ||
                        actionItem.getActionType().trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            actionName + " action type is required");
                }

                // CHANGE 1: Check if actionConfig is null
                if (actionItem.getActionConfig() == null) {
                    throw new IllegalArgumentException(
                            actionName + " action configuration is required");
                }

                // CHANGE 2: Validate actionConfig is a Map (since it's stored as Object)
                if (!(actionItem.getActionConfig() instanceof Map)) {
                    throw new IllegalArgumentException(
                            actionName + " action configuration must be a valid JSON object");
                }

                // CHANGE 3: Validate required fields based on action type
                @SuppressWarnings("unchecked")
                Map<String, Object> config = (Map<String, Object>) actionItem.getActionConfig();

                validateActionConfig(actionItem.getActionType(), config, actionName);
            }
        }
    }

    /**
     * NEW METHOD: Validates action configuration based on action type
     */
    private void validateActionConfig(String actionType, Map<String, Object> config, String actionName) {
        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException(
                    actionName + " action configuration cannot be empty");
        }

        String type = actionType.toUpperCase();

        switch (type) {
            case "EMAIL":
                if (!config.containsKey("to") || config.get("to") == null) {
                    throw new IllegalArgumentException(
                            actionName + " EMAIL action requires 'to' field");
                }
                if (!config.containsKey("subject") || config.get("subject") == null) {
                    throw new IllegalArgumentException(
                            actionName + " EMAIL action requires 'subject' field");
                }
                if (!config.containsKey("body") || config.get("body") == null) {
                    throw new IllegalArgumentException(
                            actionName + " EMAIL action requires 'body' field");
                }
                break;

            case "SMS":
                if (!config.containsKey("phoneNumber") || config.get("phoneNumber") == null) {
                    throw new IllegalArgumentException(
                            actionName + " SMS action requires 'phoneNumber' field");
                }
                if (!config.containsKey("message") || config.get("message") == null) {
                    throw new IllegalArgumentException(
                            actionName + " SMS action requires 'message' field");
                }
                break;

            case "WEBHOOK":
                if (!config.containsKey("url") || config.get("url") == null) {
                    throw new IllegalArgumentException(
                            actionName + " WEBHOOK action requires 'url' field");
                }
                break;

            case "DATABASE_UPDATE":
                if (!config.containsKey("table") || config.get("table") == null) {
                    throw new IllegalArgumentException(
                            actionName + " DATABASE_UPDATE action requires 'table' field");
                }
                if (!config.containsKey("data") || config.get("data") == null) {
                    throw new IllegalArgumentException(
                            actionName + " DATABASE_UPDATE action requires 'data' field");
                }
                break;

            case "QUEUE_MESSAGE":
                if (!config.containsKey("queueName") || config.get("queueName") == null) {
                    throw new IllegalArgumentException(
                            actionName + " QUEUE_MESSAGE action requires 'queueName' field");
                }
                if (!config.containsKey("message") || config.get("message") == null) {
                    throw new IllegalArgumentException(
                            actionName + " QUEUE_MESSAGE action requires 'message' field");
                }
                break;

            case "SLACK_NOTIFICATION":
                if (!config.containsKey("webhookUrl") || config.get("webhookUrl") == null) {
                    throw new IllegalArgumentException(
                            actionName + " SLACK_NOTIFICATION action requires 'webhookUrl' field");
                }
                if (!config.containsKey("channel") || config.get("channel") == null) {
                    throw new IllegalArgumentException(
                            actionName + " SLACK_NOTIFICATION action requires 'channel' field");
                }
                if (!config.containsKey("message") || config.get("message") == null) {
                    throw new IllegalArgumentException(
                            actionName + " SLACK_NOTIFICATION action requires 'message' field");
                }
                break;

            case "TEAMS_NOTIFICATION":
                if (!config.containsKey("webhookUrl") || config.get("webhookUrl") == null) {
                    throw new IllegalArgumentException(
                            actionName + " TEAMS_NOTIFICATION action requires 'webhookUrl' field");
                }
                if (!config.containsKey("message") || config.get("message") == null) {
                    throw new IllegalArgumentException(
                            actionName + " TEAMS_NOTIFICATION action requires 'message' field");
                }
                break;

            case "CREATE_TASK":
                if (!config.containsKey("assignee") || config.get("assignee") == null) {
                    throw new IllegalArgumentException(
                            actionName + " CREATE_TASK action requires 'assignee' field");
                }
                if (!config.containsKey("title") || config.get("title") == null) {
                    throw new IllegalArgumentException(
                            actionName + " CREATE_TASK action requires 'title' field");
                }
                break;

            case "UPDATE_STATUS":
                if (!config.containsKey("status") || config.get("status") == null) {
                    throw new IllegalArgumentException(
                            actionName + " UPDATE_STATUS action requires 'status' field");
                }
                break;

            default:
                // For unknown action types, just log a warning
                log.warn("Unknown action type '{}' in {} - skipping validation", type, actionName);
        }
    }
}