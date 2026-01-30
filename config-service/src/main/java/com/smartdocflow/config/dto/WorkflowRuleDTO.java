package com.smartdocflow.config.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Workflow Rule DTO - Shared across Config and Worker services
 * This ensures type safety and consistency across the entire system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowRuleDTO {

    private Long id;

    @NotBlank(message = "Rule name is required")
    private String ruleName;

    private String ruleDescription;

    @NotBlank(message = "Document type is required")
    private String documentType;

    @NotNull(message = "Priority is required")
    @Min(value = 1, message = "Priority must be at least 1")
    private Integer priority;

    private Boolean active;

    @Valid
    private List<RuleCondition> conditions;

    private String conditionLogic; // AND, OR, CUSTOM

    private String customLogic; // e.g., "(1 AND 2) OR (3 AND 4)"

    private List<String> requiredFields;

    private String missingFieldAction; // REJECT, MANUAL_REVIEW

    @Valid
    private DecisionAction autoApprovalAction;

    @Valid
    private DecisionAction manualReviewAction;

    @Valid
    private DecisionAction rejectionAction;

    /**
     * Rule Condition - Defines a single condition to evaluate
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RuleCondition {
        private String conditionId;

        @NotBlank(message = "Field name is required")
        private String field;

        @NotBlank(message = "Operator is required")
        private String operator; // GT, GTE, LT, LTE, EQ, NEQ, CONTAINS, RANGE, IN, NOT_IN, REGEX

        private Object value;
        private Object minValue; // For RANGE operator
        private Object maxValue; // For RANGE operator
        private List<Object> values; // For IN/NOT_IN operators
        private Boolean mandatory;
    }

    /**
     * Decision Action - Defines what happens when a decision is made
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DecisionAction {
        private String decision; // For backward compatibility, not actively used
        private String reason;

        @Valid
        private List<ActionItem> actions;

        /**
         * Action Item - A single action to execute
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class ActionItem {
            @NotBlank(message = "Action type is required")
            private String actionType; // EMAIL, SMS, WEBHOOK, DATABASE_UPDATE, etc.

            private Boolean async;
            private Integer retryCount;

            @NotNull(message = "Action configuration is required")
            private Object actionConfig; // Map<String, Object> - stores config as JSON
        }
    }
}