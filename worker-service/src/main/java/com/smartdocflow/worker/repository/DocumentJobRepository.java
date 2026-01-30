package com.smartdocflow.worker.repository;

import com.smartdocflow.worker.entity.DocumentJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentJobRepository extends JpaRepository<DocumentJob, String> {
}
