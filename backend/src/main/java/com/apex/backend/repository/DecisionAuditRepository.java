package com.apex.backend.repository;

import com.apex.backend.model.DecisionAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface DecisionAuditRepository extends JpaRepository<DecisionAudit, Long> {
    long deleteByDecisionTimeBefore(LocalDateTime cutoff);
}
