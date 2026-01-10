package com.apex.backend.repository;

import com.apex.backend.model.StrategyHealthState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StrategyHealthStateRepository extends JpaRepository<StrategyHealthState, Long> {
    Optional<StrategyHealthState> findTopByUserIdOrderByCreatedAtDesc(Long userId);
}
