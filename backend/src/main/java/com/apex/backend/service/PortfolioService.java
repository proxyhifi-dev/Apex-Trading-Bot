package com.apex.backend.service;

import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final TradeRepository tradeRepository;

    @Value("${apex.trading.capital:100000}")
    private double initialCapital;

    /**
     * Get current portfolio value
     */
    public double getPortfolioValue(boolean isPaper) {
        try {
            Double totalPnl = tradeRepository.getTotalPnlByMode(isPaper);
            double pnl = (totalPnl != null) ? totalPnl : 0;
            return initialCapital + pnl;
        } catch (Exception e) {
            log.error("Failed to calculate portfolio value", e);
            return initialCapital;
        }
    }

    /**
     * Get available cash
     */
    public double getAvailableCash(boolean isPaper) {
        try {
            List<Trade> openTrades = tradeRepository.findByIsPaperTradeAndStatus(isPaper, Trade.TradeStatus.OPEN);
            double usedCapital = openTrades.stream()
                    .mapToDouble(t -> t.getQuantity() * t.getEntryPrice())
                    .sum();
            return initialCapital - usedCapital;
        } catch (Exception e) {
            log.error("Failed to calculate available cash", e);
            return initialCapital;
        }
    }

    /**
     * Get total invested
     */
    public double getTotalInvested(boolean isPaper) {
        try {
            List<Trade> openTrades = tradeRepository.findByIsPaperTradeAndStatus(isPaper, Trade.TradeStatus.OPEN);
            return openTrades.stream()
                    .mapToDouble(t -> t.getQuantity() * t.getEntryPrice())
                    .sum();
        } catch (Exception e) {
            log.error("Failed to calculate total invested", e);
            return 0;
        }
    }

    /**
     * Get realized P&L
     */
    public double getRealizedPnL(boolean isPaper) {
        try {
            Double totalPnl = tradeRepository.getTotalPnlByMode(isPaper);
            return (totalPnl != null) ? totalPnl : 0;
        } catch (Exception e) {
            log.error("Failed to get realized P&L", e);
            return 0;
        }
    }

    /**
     * Get number of open positions
     */
    public int getOpenPositionCount(boolean isPaper) {
        try {
            List<Trade> openTrades = tradeRepository.findByIsPaperTradeAndStatus(isPaper, Trade.TradeStatus.OPEN);
            return openTrades.size();
        } catch (Exception e) {
            log.error("Failed to get open position count", e);
            return 0;
        }
    }
}
