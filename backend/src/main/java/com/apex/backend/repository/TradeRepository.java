package com.apex.backend.repository;

import com.apex.backend.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    // ✅ REQUIRED by PortfolioService
    @Query("SELECT SUM(t.realizedPnl) FROM Trade t WHERE t.isPaperTrade = :isPaper AND t.status = 'CLOSED'")
    Double getTotalPnlByMode(@Param("isPaper") boolean isPaper);

    List<Trade> findByIsPaperTradeAndStatus(boolean isPaperTrade, Trade.TradeStatus status);

    List<Trade> findByStatus(Trade.TradeStatus status);
}