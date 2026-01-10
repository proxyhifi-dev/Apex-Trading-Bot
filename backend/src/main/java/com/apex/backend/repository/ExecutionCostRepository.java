package com.apex.backend.repository;

import com.apex.backend.model.ExecutionCost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExecutionCostRepository extends JpaRepository<ExecutionCost, Long> {
    Optional<ExecutionCost> findByClientOrderId(String clientOrderId);
}
