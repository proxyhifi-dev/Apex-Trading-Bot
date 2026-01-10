package com.apex.backend.repository;

import com.apex.backend.model.BacktestResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BacktestResultRepository extends JpaRepository<BacktestResult, Long> {
    Optional<BacktestResult> findByIdAndUserId(Long id, Long userId);
}
