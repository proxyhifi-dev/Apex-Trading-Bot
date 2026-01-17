package com.apex.backend.repository;

import com.apex.backend.model.RiskLimits;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RiskLimitsRepository extends JpaRepository<RiskLimits, Long> {
    Optional<RiskLimits> findByUserId(Long userId);
}
