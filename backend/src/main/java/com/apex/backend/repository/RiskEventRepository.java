package com.apex.backend.repository;

import com.apex.backend.model.RiskEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface RiskEventRepository extends JpaRepository<RiskEvent, Long> {
    Page<RiskEvent> findByUserIdAndCreatedAtBetween(Long userId, Instant from, Instant to, Pageable pageable);
    Page<RiskEvent> findByUserIdAndTypeAndCreatedAtBetween(Long userId, String type, Instant from, Instant to, Pageable pageable);
}
