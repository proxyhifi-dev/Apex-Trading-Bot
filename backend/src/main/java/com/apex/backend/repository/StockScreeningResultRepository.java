package com.apex.backend.repository;

import com.apex.backend.model.StockScreeningResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockScreeningResultRepository extends JpaRepository<StockScreeningResult, Long> {

    // ✅ ADDED THIS MISSING METHOD
    boolean existsByUserIdAndSymbolAndScanTimeAfter(Long userId, String symbol, LocalDateTime scanTime);

    List<StockScreeningResult> findByUserIdAndApprovalStatus(Long userId, StockScreeningResult.ApprovalStatus status);

    Optional<StockScreeningResult> findByUserIdAndSymbolAndApprovalStatus(Long userId, String symbol, StockScreeningResult.ApprovalStatus status);

    List<StockScreeningResult> findTop50ByUserIdOrderByScanTimeDesc(Long userId);

    Optional<StockScreeningResult> findByIdAndUserId(Long id, Long userId);
}
