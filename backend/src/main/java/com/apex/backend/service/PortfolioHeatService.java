package com.apex.backend.service;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.model.Candle;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PortfolioHeatService {

    private final TradeRepository tradeRepository;
    private final FyersService fyersService;
    private final CorrelationService correlationService;
    private final AdvancedTradingProperties advancedTradingProperties;
    private final DecisionAuditService decisionAuditService;

    public boolean withinHeatLimit(Long userId, BigDecimal equity, BigDecimal entryPrice, BigDecimal stopLoss, int quantity) {
        if (equity == null || equity.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal riskPerShare = entryPrice.subtract(stopLoss).abs();
        BigDecimal newRisk = riskPerShare.multiply(BigDecimal.valueOf(quantity));
        double currentHeat = currentPortfolioHeat(userId, equity);
        double totalHeat = currentHeat + newRisk.doubleValue() / equity.doubleValue();
        boolean allowed = totalHeat <= advancedTradingProperties.getRisk().getMaxPortfolioHeatPct();
        decisionAuditService.record("PORTFOLIO", "", "PORTFOLIO_HEAT", Map.of(
                "currentHeat", currentHeat,
                "newRisk", newRisk.doubleValue(),
                "totalHeat", totalHeat,
                "allowed", allowed
        ));
        return allowed;
    }

    public double currentPortfolioHeat(Long userId, BigDecimal equity) {
        if (equity == null || equity.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        List<Trade> openTrades = tradeRepository.findByUserIdAndStatus(userId, Trade.TradeStatus.OPEN);
        double totalRisk = 0.0;
        for (Trade trade : openTrades) {
            if (trade.getEntryPrice() == null || trade.getStopLoss() == null) {
                continue;
            }
            BigDecimal riskPerShare = trade.getEntryPrice().subtract(trade.getStopLoss()).abs();
            totalRisk += riskPerShare.multiply(BigDecimal.valueOf(trade.getQuantity())).doubleValue();
        }
        return totalRisk / equity.doubleValue();
    }

    public boolean passesCorrelationCheck(String symbol, Long userId) {
        List<Trade> openTrades = tradeRepository.findByUserIdAndStatus(userId, Trade.TradeStatus.OPEN);
        if (openTrades.isEmpty()) {
            return true;
        }
        int lookback = advancedTradingProperties.getRisk().getCorrelationLookback();
        List<Candle> newHistory = fyersService.getHistoricalData(symbol, lookback, "5");
        if (newHistory.size() < lookback) {
            return true;
        }
        List<Double> newSeries = newHistory.stream().map(Candle::getClose).toList();
        for (Trade trade : openTrades) {
            List<Candle> openHistory = fyersService.getHistoricalData(trade.getSymbol(), lookback, "5");
            if (openHistory.size() < lookback) {
                continue;
            }
            List<Double> openSeries = openHistory.stream().map(Candle::getClose).toList();
            double corr = correlationService.calculateCorrelation(newSeries, openSeries);
            if (corr >= advancedTradingProperties.getRisk().getCorrelationThreshold()) {
                decisionAuditService.record(symbol, "5m", "CORRELATION", Map.of(
                        "openSymbol", trade.getSymbol(),
                        "correlation", corr,
                        "allowed", false
                ));
                return false;
            }
        }
        decisionAuditService.record(symbol, "5m", "CORRELATION", Map.of("allowed", true));
        return true;
    }
}
