package com.smartdocflow.worker.engine;

import com.smartdocflow.worker.dto.WorkflowRuleDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
public class RuleEngine {

    /**
     * Evaluates a single rule against extracted data - NOW USES POJO
     * @param rule The rule to evaluate (POJO instead of Map)
     * @param extractedData The data extracted from document
     * @return RuleEvaluationResult containing decision and actions
     */
    public RuleEvaluationResult evaluateRule(WorkflowRuleDTO rule,
                                             Map<String, Object> extractedData) {
        log.debug("Evaluating rule: {}", rule.getRuleName());

        try {
            // Step 1: Validate required fields
            FieldValidationResult fieldValidation =
                    validateRequiredFields(rule.getRequiredFields(), extractedData);

            if (!fieldValidation.isValid()) {
                return handleMissingFields(rule, fieldValidation);
            }

            // Step 2: Evaluate conditions
            boolean conditionsMet = evaluateConditions(
                    rule.getConditions(),
                    extractedData,
                    rule.getConditionLogic(),
                    rule.getCustomLogic()
            );

            // Step 3: Determine decision and return appropriate action
            if (conditionsMet) {
                return buildResult(rule, rule.getAutoApprovalAction(),
                        "AUTO_APPROVED", "All conditions met for automatic approval",
                        rule.getRuleName());
            } else {
                // Check if any mandatory condition failed
                boolean mandatoryFailed = hasMandatoryConditionFailed(
                        rule.getConditions(), extractedData);

                if (mandatoryFailed) {
                    return buildResult(rule, rule.getRejectionAction(),
                            "REJECTED", "Mandatory conditions not met", rule.getRuleName());
                } else {
                    return buildResult(rule, rule.getManualReviewAction(),
                            "MANUAL_REVIEW_REQUIRED",
                            "Conditions partially met - requires manual review",
                            rule.getRuleName());
                }
            }

        } catch (Exception e) {
            log.error("Error evaluating rule: {}", rule.getRuleName(), e);
            return RuleEvaluationResult.builder()
                    .matched(false)
                    .decision("ERROR")
                    .reason("Rule evaluation failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Validates that all required fields are present in extracted data
     */
    private FieldValidationResult validateRequiredFields(List<String> requiredFields,
                                                         Map<String, Object> extractedData) {
        if (requiredFields == null || requiredFields.isEmpty()) {
            return FieldValidationResult.builder().valid(true).build();
        }

        List<String> missingFields = new ArrayList<>();
        List<String> nullFields = new ArrayList<>();

        for (String field : requiredFields) {
            Object value = getNestedValue(extractedData, field);
            if (value == null) {
                missingFields.add(field);
            } else if (value instanceof String && ((String) value).trim().isEmpty()) {
                nullFields.add(field);
            }
        }

        return FieldValidationResult.builder()
                .valid(missingFields.isEmpty() && nullFields.isEmpty())
                .missingFields(missingFields)
                .nullFields(nullFields)
                .build();
    }

    /**
     * Handles missing required fields scenario - NOW USES POJO
     */
    private RuleEvaluationResult handleMissingFields(WorkflowRuleDTO rule,
                                                     FieldValidationResult fieldValidation) {
        String missingFieldAction = rule.getMissingFieldAction() != null ?
                rule.getMissingFieldAction() : "REJECT";

        String reason = String.format("Missing required fields: %s. Empty fields: %s",
                fieldValidation.getMissingFields(),
                fieldValidation.getNullFields());

        if ("REJECT".equals(missingFieldAction)) {
            return buildResult(rule, rule.getRejectionAction(),
                    "REJECTED", reason, rule.getRuleName());
        } else {
            return buildResult(rule, rule.getManualReviewAction(),
                    "MANUAL_REVIEW_REQUIRED", reason, rule.getRuleName());
        }
    }

    /**
     * Evaluates all conditions based on logic (AND/OR/CUSTOM) - NOW USES POJO
     */
    private boolean evaluateConditions(List<com.smartdocflow.worker.dto.WorkflowRuleDTO.RuleCondition> conditions,
                                       Map<String, Object> extractedData,
                                       String conditionLogic,
                                       String customLogic) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        // Evaluate each condition
        Map<String, Boolean> conditionResults = new HashMap<>();

        for (WorkflowRuleDTO.RuleCondition condition : conditions) {
            String conditionId = condition.getConditionId() != null ?
                    condition.getConditionId() : UUID.randomUUID().toString();

            boolean result = evaluateSingleCondition(condition, extractedData);
            conditionResults.put(conditionId, result);

            log.debug("Condition {} [{}]: {}", conditionId,
                    condition.getField(), result);
        }

        // Apply logic
        if ("CUSTOM".equalsIgnoreCase(conditionLogic) && customLogic != null) {
            return evaluateCustomLogic(customLogic, conditionResults);
        } else if ("OR".equalsIgnoreCase(conditionLogic)) {
            return conditionResults.values().stream().anyMatch(Boolean::booleanValue);
        } else { // Default to AND
            return conditionResults.values().stream().allMatch(Boolean::booleanValue);
        }
    }

    /**
     * Evaluates a single condition - NOW USES POJO
     */
    private boolean evaluateSingleCondition(WorkflowRuleDTO.RuleCondition condition,
                                            Map<String, Object> extractedData) {
        String field = condition.getField();
        String operator = condition.getOperator();
        Object expectedValue = condition.getValue();

        Object actualValue = getNestedValue(extractedData, field);

        if (actualValue == null) {
            log.debug("Field {} not found in extracted data", field);
            return false;
        }

        try {
            switch (operator.toUpperCase()) {
                case "GT": // Greater Than
                    return compareNumeric(actualValue, expectedValue) > 0;

                case "GTE": // Greater Than or Equal
                    return compareNumeric(actualValue, expectedValue) >= 0;

                case "LT": // Less Than
                    return compareNumeric(actualValue, expectedValue) < 0;

                case "LTE": // Less Than or Equal
                    return compareNumeric(actualValue, expectedValue) <= 0;

                case "EQ": // Equal
                    return Objects.equals(normalizeValue(actualValue),
                            normalizeValue(expectedValue));

                case "NEQ": // Not Equal
                    return !Objects.equals(normalizeValue(actualValue),
                            normalizeValue(expectedValue));

                case "CONTAINS":
                    return actualValue.toString().toLowerCase()
                            .contains(expectedValue.toString().toLowerCase());

                case "NOT_CONTAINS":
                    return !actualValue.toString().toLowerCase()
                            .contains(expectedValue.toString().toLowerCase());

                case "STARTS_WITH":
                    return actualValue.toString().toLowerCase()
                            .startsWith(expectedValue.toString().toLowerCase());

                case "ENDS_WITH":
                    return actualValue.toString().toLowerCase()
                            .endsWith(expectedValue.toString().toLowerCase());

                case "RANGE": // Value must be between minValue and maxValue
                    Object minValue = condition.getMinValue();
                    Object maxValue = condition.getMaxValue();
                    return compareNumeric(actualValue, minValue) >= 0
                            && compareNumeric(actualValue, maxValue) <= 0;

                case "IN": // Value must be in the list
                    List<Object> values = condition.getValues();
                    return values != null && values.stream()
                            .anyMatch(v -> Objects.equals(
                                    normalizeValue(actualValue), normalizeValue(v)));

                case "NOT_IN": // Value must not be in the list
                    List<Object> notInValues = condition.getValues();
                    return notInValues == null || notInValues.stream()
                            .noneMatch(v -> Objects.equals(
                                    normalizeValue(actualValue), normalizeValue(v)));

                case "REGEX": // Value must match regex pattern
                    Pattern pattern = Pattern.compile(expectedValue.toString());
                    return pattern.matcher(actualValue.toString()).matches();

                default:
                    log.warn("Unknown operator: {}", operator);
                    return false;
            }
        } catch (Exception e) {
            log.error("Error evaluating condition for field {}: {}", field, e.getMessage());
            return false;
        }
    }

    /**
     * Evaluates custom logic expression like "(1 AND 2) OR (3 AND 4)"
     * FIXED: Uses regex word boundaries to prevent partial matches
     */
    private boolean evaluateCustomLogic(String customLogic,
                                        Map<String, Boolean> conditionResults) {
        try {
            String expression = customLogic.trim();

            // Replace condition IDs with their boolean values
            for (Map.Entry<String, Boolean> entry : conditionResults.entrySet()) {
                String conditionId = entry.getKey();
                String boolValue = entry.getValue().toString().toUpperCase();

                // Use word boundaries to avoid partial matches
                expression = expression.replaceAll("\\b" + conditionId + "\\b", boolValue);
            }

            log.debug("Custom logic after substitution: {}", expression);

            // Simple recursive evaluation
            return evaluateLogicExpression(expression.toUpperCase());

        } catch (Exception e) {
            log.error("Error evaluating custom logic: {}", customLogic, e);
            return false;
        }
    }

    /**
     * Recursively evaluates boolean logic expression
     */
    private boolean evaluateLogicExpression(String expression) {
        expression = expression.trim();

        // Base case: single boolean value
        if ("TRUE".equals(expression)) return true;
        if ("FALSE".equals(expression)) return false;

        // Handle parentheses
        if (expression.startsWith("(") && expression.endsWith(")")) {
            return evaluateLogicExpression(expression.substring(1, expression.length() - 1));
        }

        // Handle OR operator (lower precedence)
        int orIndex = findTopLevelOperator(expression, "OR");
        if (orIndex != -1) {
            boolean left = evaluateLogicExpression(expression.substring(0, orIndex).trim());
            boolean right = evaluateLogicExpression(expression.substring(orIndex + 2).trim());
            return left || right;
        }

        // Handle AND operator (higher precedence)
        int andIndex = findTopLevelOperator(expression, "AND");
        if (andIndex != -1) {
            boolean left = evaluateLogicExpression(expression.substring(0, andIndex).trim());
            boolean right = evaluateLogicExpression(expression.substring(andIndex + 3).trim());
            return left && right;
        }

        throw new IllegalArgumentException("Invalid expression: " + expression);
    }

    /**
     * Finds operator at top level (not inside parentheses)
     */
    private int findTopLevelOperator(String expression, String operator) {
        int depth = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth == 0 && expression.substring(i).startsWith(operator)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Checks if any mandatory condition has failed - NOW USES POJO
     */
    private boolean hasMandatoryConditionFailed(List<WorkflowRuleDTO.RuleCondition> conditions,
                                                Map<String, Object> extractedData) {
        if (conditions == null) return false;

        return conditions.stream()
                .filter(c -> Boolean.TRUE.equals(c.getMandatory()))
                .anyMatch(c -> !evaluateSingleCondition(c, extractedData));
    }

    /**
     * Builds the evaluation result - NOW USES POJO
     */
    private RuleEvaluationResult buildResult(WorkflowRuleDTO rule,
                                             WorkflowRuleDTO.DecisionAction decisionAction,
                                             String decision,
                                             String reason,
                                             String ruleName) {
        List<WorkflowRuleDTO.DecisionAction.ActionItem> actions = new ArrayList<>();
        String finalReason = reason;

        if (decisionAction != null) {
            // Use custom reason if provided
            if (decisionAction.getReason() != null) {
                finalReason = decisionAction.getReason();
            }

            // Extract actions list
            if (decisionAction.getActions() != null && !decisionAction.getActions().isEmpty()) {
                actions = decisionAction.getActions();
            }
        }

        return RuleEvaluationResult.builder()
                .matched(true)
                .ruleName(ruleName)
                .decision(decision)
                .reason(finalReason)
                .actions(actions)
                .build();
    }

    /**
     * Gets nested value from map using dot notation (e.g., "applicant.address.city")
     */
    private Object getNestedValue(Map<String, Object> data, String field) {
        if (field == null || data == null) return null;

        String[] parts = field.split("\\.");
        Object current = data;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * Compares two values numerically
     */
    private int compareNumeric(Object actual, Object expected) {
        BigDecimal actualDecimal = toBigDecimal(actual);
        BigDecimal expectedDecimal = toBigDecimal(expected);
        return actualDecimal.compareTo(expectedDecimal);
    }

    /**
     * Converts object to BigDecimal for numeric comparison
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    /**
     * Normalizes value for comparison
     */
    private Object normalizeValue(Object value) {
        if (value instanceof String) {
            return ((String) value).trim().toLowerCase();
        }
        return value;
    }

    // Supporting classes

    /**
     * Rule Evaluation Result - NOW USES POJO FOR ACTIONS
     */
    @lombok.Data
    @lombok.Builder
    public static class RuleEvaluationResult {
        private boolean matched;
        private String ruleName;
        private String decision;
        private String reason;
        private List<WorkflowRuleDTO.DecisionAction.ActionItem> actions;  // Type-safe!
    }

    @lombok.Data
    @lombok.Builder
    private static class FieldValidationResult {
        private boolean valid;
        private List<String> missingFields;
        private List<String> nullFields;
    }
}