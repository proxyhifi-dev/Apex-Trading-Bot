package com.apex.backend.repository;

import com.apex.backend.model.StockScreeningResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockScreeningResultRepository extends JpaRepository<StockScreeningResult, Long> {

    List<StockScreeningResult> findByApprovalStatus(StockScreeningResult.ApprovalStatus status);

    // ✅ FIXED: Added missing method
    Optional<StockScreeningResult> findBySymbolAndApprovalStatus(String symbol, StockScreeningResult.ApprovalStatus status);
}