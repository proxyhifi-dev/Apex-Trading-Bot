package com.apex.backend.service;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Trade;
import com.apex.backend.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ExitPriorityEngine {

    private final StrategyProperties strategyProperties;

    public ExitDecision evaluate(Trade trade, BigDecimal currentPrice, int barsHeld, boolean technicalExit) {
        if (trade.getEntryPrice() == null || trade.getStopLoss() == null) {
            return ExitDecision.hold(trade.getCurrentStopLoss(), "MISSING_DATA");
        }
        BigDecimal entry = trade.getEntryPrice();
        BigDecimal stop = trade.getCurrentStopLoss() != null ? trade.getCurrentStopLoss() : trade.getStopLoss();
        BigDecimal initialRisk = entry.subtract(trade.getStopLoss()).abs();
        if (initialRisk.compareTo(BigDecimal.ZERO) == 0) {
            return ExitDecision.hold(stop, "NO_RISK");
        }
        BigDecimal profit = trade.getTradeType() == Trade.TradeType.LONG
                ? currentPrice.subtract(entry)
                : entry.subtract(currentPrice);
        BigDecimal profitR = profit.divide(initialRisk, MoneyUtils.SCALE, java.math.RoundingMode.HALF_UP);

        // Hard stop-loss
        boolean stopHit = trade.getTradeType() == Trade.TradeType.LONG
                ? currentPrice.compareTo(stop) <= 0
                : currentPrice.compareTo(stop) >= 0;
        if (stopHit) {
            return ExitDecision.exit(currentPrice, stop, Trade.ExitReason.STOP_LOSS, "HARD_SL");
        }

        // Time stop
        if (barsHeld >= strategyProperties.getExit().getTimeStopBars()) {
            return ExitDecision.exit(currentPrice, stop, Trade.ExitReason.TIME_EXIT, "TIME_STOP");
        }

        // Breakeven move at +2R
        if (!trade.isBreakevenMoved() && profitR.compareTo(BigDecimal.valueOf(2.0)) >= 0) {
            trade.setBreakevenMoved(true);
            stop = entry;
        }

        // Trailing only after +3R using ATR distance
        if (profitR.compareTo(BigDecimal.valueOf(3.0)) >= 0 && trade.getAtr() != null) {
            BigDecimal trailDistance = trade.getAtr().multiply(BigDecimal.valueOf(strategyProperties.getExit().getTrailingMultiplier()));
            if (trade.getTradeType() == Trade.TradeType.LONG) {
                BigDecimal proposed = currentPrice.subtract(trailDistance);
                if (proposed.compareTo(stop) > 0) {
                    stop = proposed;
                }
            } else {
                BigDecimal proposed = currentPrice.add(trailDistance);
                if (proposed.compareTo(stop) < 0) {
                    stop = proposed;
                }
            }
        }

        if (technicalExit) {
            return ExitDecision.exit(currentPrice, stop, Trade.ExitReason.SIGNAL, "TECHNICAL_EXIT");
        }

        return ExitDecision.hold(stop, "HOLD");
    }

    public record ExitDecision(boolean shouldExit, BigDecimal exitPrice, BigDecimal newStopLoss,
                               Trade.ExitReason reason, String reasonDetail) {
        public static ExitDecision exit(BigDecimal exitPrice, BigDecimal newStopLoss, Trade.ExitReason reason, String detail) {
            return new ExitDecision(true, exitPrice, newStopLoss, reason, detail);
        }

        public static ExitDecision hold(BigDecimal newStopLoss, String detail) {
            return new ExitDecision(false, null, newStopLoss, null, detail);
        }
    }
}
