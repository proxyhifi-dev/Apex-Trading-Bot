package com.apex.backend.repository;

import com.apex.backend.model.CircuitBreakerLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CircuitBreakerLogRepository extends JpaRepository<CircuitBreakerLog, Long> {
}