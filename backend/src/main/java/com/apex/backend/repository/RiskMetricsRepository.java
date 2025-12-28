package com.apex.backend.repository;

import com.apex.backend.model.RiskMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface RiskMetricsRepository extends JpaRepository<RiskMetrics, Long> {
    Optional<RiskMetrics> findByTradingDate(LocalDate date);
}
