package com.apex.backend.service;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.model.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LiquidityValidator {

    private final AdvancedTradingProperties advancedTradingProperties;
    private final DecisionAuditService decisionAuditService;

    public LiquidityDecision validate(String symbol, List<Candle> dailyCandles, int desiredQty) {
        if (dailyCandles == null || dailyCandles.isEmpty()) {
            return new LiquidityDecision(false, 0, "NO_DATA", 0.0);
        }
        Candle latest = dailyCandles.get(dailyCandles.size() - 1);
        double price = latest.getClose();
        double rupeeVolume = price * latest.getVolume();
        AdvancedTradingProperties.Liquidity cfg = advancedTradingProperties.getLiquidity();
        if (rupeeVolume < cfg.getMinRupeeVolume()) {
            audit(symbol, "REJECTED", "MIN_RUPEE_VOLUME", rupeeVolume);
            return new LiquidityDecision(false, 0, "MIN_RUPEE_VOLUME", 0.0);
        }
        double spreadPct = price == 0 ? 0.0 : ((latest.getHigh() - latest.getLow()) / price) * 100.0;
        if (spreadPct > cfg.getMaxSpreadPct()) {
            audit(symbol, "REJECTED", "MAX_SPREAD", spreadPct);
            return new LiquidityDecision(false, 0, "MAX_SPREAD", spreadPct);
        }
        double maxOrderValue = rupeeVolume * cfg.getMaxOrderPctDailyVolume();
        int maxQty = (int) Math.floor(maxOrderValue / price);
        int adjustedQty = Math.min(desiredQty, maxQty);
        if (adjustedQty <= 0) {
            audit(symbol, "REJECTED", "MAX_ORDER_PCT", maxOrderValue);
            return new LiquidityDecision(false, 0, "MAX_ORDER_PCT", maxOrderValue);
        }
        double orderValue = adjustedQty * price;
        double impact = cfg.getImpactLambda() * price * Math.sqrt(orderValue / rupeeVolume);
        LiquidityDecision decision = new LiquidityDecision(true, adjustedQty, "OK", impact);
        audit(symbol, "APPROVED", "OK", impact);
        return decision;
    }

    private void audit(String symbol, String status, String reason, double value) {
        decisionAuditService.record(symbol, "1d", "LIQUIDITY", Map.of(
                "status", status,
                "reason", reason,
                "value", value
        ));
    }

    public record LiquidityDecision(boolean allowed, int adjustedQty, String reason, double estimatedImpact) {}
}
