package com.apex.backend.repository;

import com.apex.backend.model.DecisionAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionAuditRepository extends JpaRepository<DecisionAudit, Long> {}
