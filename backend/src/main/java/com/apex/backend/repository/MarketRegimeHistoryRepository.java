package com.apex.backend.repository;

import com.apex.backend.model.MarketRegimeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketRegimeHistoryRepository extends JpaRepository<MarketRegimeHistory, Long> {
    List<MarketRegimeHistory> findTop200BySymbolAndTimeframeOrderByDetectedAtDesc(String symbol, String timeframe);
}
