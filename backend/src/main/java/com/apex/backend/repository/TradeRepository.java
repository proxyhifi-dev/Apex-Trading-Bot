package com.apex.backend.repository;

import com.apex.backend.model.PositionState;
import com.apex.backend.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByUserId(Long userId);
    List<Trade> findByUserIdAndSymbol(Long userId, String symbol);
    List<Trade> findByUserIdAndSymbolAndIsPaperTradeOrderByEntryTimeAsc(Long userId, String symbol, boolean isPaperTrade);
    List<Trade> findByUserIdAndStatus(Long userId, Trade.TradeStatus status);
    List<Trade> findByUserIdAndIsPaperTrade(Long userId, boolean isPaperTrade);
    List<Trade> findByUserIdAndTradeType(Long userId, Trade.TradeType tradeType);
    List<Trade> findByUserIdAndIsPaperTradeAndStatus(Long userId, boolean isPaperTrade, Trade.TradeStatus status);
    List<Trade> findTop50ByUserIdAndStatusOrderByExitTimeDesc(Long userId, Trade.TradeStatus status);
    List<Trade> findByPositionStateAndStopAckedAtIsNullAndEntryTimeBefore(PositionState positionState, java.time.LocalDateTime before);

    long countByUserIdAndStatus(Long userId, Trade.TradeStatus status);

    @Query("SELECT SUM(t.realizedPnl) FROM Trade t WHERE t.userId = :userId AND t.isPaperTrade = :isPaper AND t.status = 'CLOSED'")
    java.math.BigDecimal getTotalPnlByUserAndMode(@Param("userId") Long userId, @Param("isPaper") boolean isPaper);
}
