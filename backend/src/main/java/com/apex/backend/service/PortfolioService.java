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

    // NEW: Added missing method
    public double getAvailableEquity(boolean isPaper) {
        try {
            return getAvailableCash(isPaper);
        } catch (Exception e) {
            log.error("Failed to get available equity", e);
            return initialCapital;
        }
    }

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

    public double getRealizedPnL(boolean isPaper) {
        try {
            Double totalPnl = tradeRepository.getTotalPnlByMode(isPaper);
            return (totalPnl != null) ? totalPnl : 0;
        } catch (Exception e) {
            log.error("Failed to get realized P&L", e);
            return 0;
        }
    }

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
