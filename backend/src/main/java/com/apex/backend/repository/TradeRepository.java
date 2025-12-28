package com.apex.backend.repository;

import com.apex.backend.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    // Find all trades by status
    List<Trade> findByStatus(Trade.TradeStatus status);

    // Find trades by isPaperTrade and status
    List<Trade> findByIsPaperTradeAndStatus(boolean isPaperTrade, Trade.TradeStatus status);

    // Find all paper trades
    List<Trade> findByIsPaperTrade(boolean isPaperTrade);
}
