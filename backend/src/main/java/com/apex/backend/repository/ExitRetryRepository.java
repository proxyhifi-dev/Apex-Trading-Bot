package com.apex.backend.repository;

import com.apex.backend.model.ExitRetryRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ExitRetryRepository extends JpaRepository<ExitRetryRequest, Long> {
    List<ExitRetryRequest> findByResolvedFalseAndNextAttemptAtBefore(Instant now);
    List<ExitRetryRequest> findByTradeIdAndResolvedFalse(Long tradeId);
}
