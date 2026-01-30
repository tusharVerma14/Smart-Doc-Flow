package com.smartdocflow.config.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---------------- BASIC METADATA ----------------

    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    @Column(name = "rule_description", length = 500)
    private String ruleDescription;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    // ---------------- CONDITIONS ----------------

    @Column(name = "conditions", columnDefinition = "TEXT")
    private String conditions;

    @Column(name = "condition_logic", nullable = false, length = 10)
    private String conditionLogic;

    @Column(name = "custom_logic", columnDefinition = "TEXT")
    private String customLogic;

    // ---------------- REQUIRED FIELDS ----------------

    @Column(name = "required_fields", columnDefinition = "TEXT")
    private String requiredFields;

    @Column(name = "missing_field_action", length = 50)
    private String missingFieldAction;

    // ---------------- DECISION ACTIONS ----------------

    @Column(name = "auto_approval_action", columnDefinition = "TEXT")
    private String autoApprovalAction;

    @Column(name = "manual_review_action", columnDefinition = "TEXT")
    private String manualReviewAction;

    @Column(name = "rejection_action", columnDefinition = "TEXT")
    private String rejectionAction;

    // ---------------- AUDIT FIELDS ----------------

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}