package com.apex.backend.repository;

import com.apex.backend.model.TradeFeatureAttribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeFeatureAttributionRepository extends JpaRepository<TradeFeatureAttribution, Long> {
    List<TradeFeatureAttribution> findByTradeIdAndUserId(Long tradeId, Long userId);
}
