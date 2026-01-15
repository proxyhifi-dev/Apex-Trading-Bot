package com.apex.backend.service;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.config.StrategyConfig;
import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class HybridPositionSizingService {

    private final StrategyConfig strategyConfig;
    private final StrategyProperties strategyProperties;
    private final AdvancedTradingProperties advancedTradingProperties;
    private final TradeRepository tradeRepository;

    public int calculateQuantity(BigDecimal equity, BigDecimal entryPrice, BigDecimal stopLoss, BigDecimal atr, Long userId) {
        return calculateSizing(equity, entryPrice, stopLoss, atr, userId, null).quantity();
    }

    public SizingResult calculateSizing(BigDecimal equity, BigDecimal entryPrice, BigDecimal stopLoss, BigDecimal atr, Long userId, Double score) {
        if (equity == null || equity.compareTo(BigDecimal.ZERO) <= 0) {
            return new SizingResult(0, 1.0);
        }
        BigDecimal riskPerShare = entryPrice.subtract(stopLoss).abs();
        if (riskPerShare.compareTo(BigDecimal.ZERO) == 0 && atr != null) {
            riskPerShare = atr.multiply(BigDecimal.valueOf(strategyProperties.getAtr().getStopMultiplier()));
        }
        if (riskPerShare.compareTo(BigDecimal.ZERO) == 0) {
            return new SizingResult(0, 1.0);
        }
        BigDecimal baseRisk = equity.multiply(BigDecimal.valueOf(strategyProperties.getSizing().getBaseRisk()));
        int atrSize = baseRisk.divide(riskPerShare, 0, RoundingMode.DOWN).intValue();

        int kellySize = calculateKellySize(equity, riskPerShare, userId);
        int baseSize = Math.min(atrSize, kellySize);
        double multiplier = resolveDynamicMultiplier(score);
        int scaledSize = (int) Math.floor(baseSize * multiplier);

        BigDecimal maxCapital = equity.multiply(BigDecimal.valueOf(strategyConfig.getRisk().getMaxSingleTradeCapitalPct()));
        int maxByCapital = maxCapital.divide(entryPrice, 0, RoundingMode.DOWN).intValue();
        int size = Math.min(scaledSize, maxByCapital);
        return new SizingResult(Math.max(size, 0), multiplier);
    }

    public double resolveDynamicMultiplier(Double score) {
        StrategyProperties.Dynamic cfg = strategyProperties.getSizing().getDynamic();
        if (!cfg.isEnabled() || score == null) {
            return 1.0;
        }
        if (score <= cfg.getScoreFloor()) {
            return cfg.getMinMultiplier();
        }
        if (score >= cfg.getScoreCeil()) {
            return cfg.getMaxMultiplier();
        }
        double ratio = (score - cfg.getScoreFloor()) / (cfg.getScoreCeil() - cfg.getScoreFloor());
        return cfg.getMinMultiplier() + (ratio * (cfg.getMaxMultiplier() - cfg.getMinMultiplier()));
    }

    private int calculateKellySize(BigDecimal equity, BigDecimal riskPerShare, Long userId) {
        if (userId == null) {
            return Integer.MAX_VALUE;
        }
        int lookback = advancedTradingProperties.getRisk().getKellyLookbackTrades();
        List<Trade> closed = tradeRepository.findTop50ByUserIdAndStatusOrderByExitTimeDesc(userId, Trade.TradeStatus.CLOSED);
        if (closed.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        List<Trade> slice = closed.size() > lookback ? closed.subList(0, lookback) : closed;
        double wins = 0;
        double totalWins = 0;
        double totalLosses = 0;
        for (Trade trade : slice) {
            if (trade.getRealizedPnl() == null) {
                continue;
            }
            double pnl = trade.getRealizedPnl().doubleValue();
            if (pnl > 0) {
                wins++;
                totalWins += pnl;
            } else if (pnl < 0) {
                totalLosses += Math.abs(pnl);
            }
        }
        double totalTrades = slice.size();
        if (totalTrades == 0 || totalLosses == 0) {
            return Integer.MAX_VALUE;
        }
        double winRate = wins / totalTrades;
        double payoffRatio = totalWins / totalLosses;
        double kellyFraction = winRate - (1 - winRate) / payoffRatio;
        kellyFraction = Math.max(0.0, kellyFraction * advancedTradingProperties.getRisk().getKellyFraction());
        if (kellyFraction == 0.0) {
            return 0;
        }
        BigDecimal riskBudget = equity.multiply(BigDecimal.valueOf(kellyFraction));
        int size = riskBudget.divide(riskPerShare, 0, RoundingMode.DOWN).intValue();
        log.info("Kelly sizing: winRate={}, payoffRatio={}, kellyFraction={}, size={}", winRate, payoffRatio, kellyFraction, size);
        return size;
    }

    public record SizingResult(int quantity, double dynamicMultiplier) {}
}
