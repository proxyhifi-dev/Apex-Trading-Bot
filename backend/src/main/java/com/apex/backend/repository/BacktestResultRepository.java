package com.apex.backend.repository;

import com.apex.backend.model.BacktestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface BacktestResultRepository extends JpaRepository<BacktestResult, Long> {
    Optional<BacktestResult> findByIdAndUserId(Long id, Long userId);
    Page<BacktestResult> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
