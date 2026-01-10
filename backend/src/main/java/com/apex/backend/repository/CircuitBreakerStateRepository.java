package com.apex.backend.repository;

import com.apex.backend.model.CircuitBreakerState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CircuitBreakerStateRepository extends JpaRepository<CircuitBreakerState, Long> {}
