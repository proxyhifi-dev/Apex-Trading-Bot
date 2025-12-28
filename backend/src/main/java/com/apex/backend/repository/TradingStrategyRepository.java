package com.apex.backend.repository;

import com.apex.backend.model.TradingStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradingStrategyRepository extends JpaRepository<TradingStrategy, Long> {

    // ✅ CHANGED FROM findByIsActiveTrue() to findByActiveTrue()
    List<TradingStrategy> findByActiveTrue();
}
