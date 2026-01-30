package com.smartdocflow.config.repository;

import com.smartdocflow.config.entity.WorkflowRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import com.smartdocflow.config.entity.WorkflowRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowRuleRepository extends JpaRepository<WorkflowRule, Long> {

    List<WorkflowRule> findByDocumentTypeAndActiveOrderByPriorityAsc(
            String documentType, Boolean active);

    List<WorkflowRule> findByActiveOrderByPriorityAsc(Boolean active);

    List<WorkflowRule> findByDocumentType(String documentType);
}