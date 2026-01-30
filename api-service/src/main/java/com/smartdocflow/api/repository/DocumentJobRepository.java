package com.smartdocflow.api.repository;

import com.smartdocflow.api.entity.DocumentJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DocumentJobRepository extends JpaRepository<DocumentJob, String> {
    List<DocumentJob> findByUserId(String userId);
}
