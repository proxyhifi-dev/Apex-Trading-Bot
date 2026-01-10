package com.apex.backend.repository;

import com.apex.backend.model.CorrelationRegimeState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CorrelationRegimeStateRepository extends JpaRepository<CorrelationRegimeState, Long> {
    Optional<CorrelationRegimeState> findTopByUserIdOrderByCreatedAtDesc(Long userId);
}
