package com.apex.backend.repository;

import com.apex.backend.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    // Find trades by symbol
    List<Trade> findBySymbol(String symbol);

    // Find trades by status
    List<Trade> findByStatus(Trade.TradeStatus status);

    // Find paper trades
    List<Trade> findByIsPaperTrade(boolean isPaperTrade);

    // Find trades by type
    List<Trade> findByTradeType(Trade.TradeType tradeType);
}
