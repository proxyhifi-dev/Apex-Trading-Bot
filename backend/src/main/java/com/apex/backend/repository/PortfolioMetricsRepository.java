package com.apex.backend.repository;

import com.apex.backend.model.PortfolioMetrics;
import com.apex.backend.model.TradingStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PortfolioMetricsRepository extends JpaRepository<PortfolioMetrics, Long> {

    // ✅ FIXED: Use @Query instead of derived method name because field is "timestamp" not "date"
    @Query("SELECT p FROM PortfolioMetrics p WHERE p.strategy = :strategy AND DATE(p.timestamp) = :date")
    Optional<PortfolioMetrics> findByStrategyAndDate(
            @Param("strategy") TradingStrategy strategy,
            @Param("date") LocalDate date
    );
}
