package com.apex.backend.service;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.model.Candle;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LiquidityGateService {

    private final AdvancedTradingProperties advancedTradingProperties;

    public LiquidityGateService(AdvancedTradingProperties advancedTradingProperties) {
        this.advancedTradingProperties = advancedTradingProperties;
    }

    public record LiquidityDecision(boolean allowed, String reason, double rupeeVolume, double spreadPct, double avgVolume) {}

    public LiquidityDecision evaluate(String symbol, List<Candle> candles, double lastPrice) {
        AdvancedTradingProperties.Liquidity cfg = advancedTradingProperties.getLiquidity();
        if (!cfg.isGateEnabled()) {
            return new LiquidityDecision(true, "Liquidity gate disabled", 0.0, 0.0, 0.0);
        }
        String sanitized = sanitizeSymbol(symbol);
        if (sanitized == null) {
            return new LiquidityDecision(false, "Invalid symbol", 0.0, 0.0, 0.0);
        }
        if (candles == null || candles.isEmpty()) {
            return new LiquidityDecision(false, "Missing candle data", 0.0, 0.0, 0.0);
        }
        double avgVolume = averageVolume(candles, 20);
        double rupeeVolume = avgVolume * Math.max(lastPrice, 0.0);
        double spreadPct = proxySpreadPct(candles);

        if (rupeeVolume < cfg.getMinRupeeVolume()) {
            return new LiquidityDecision(false, "Rupee volume below minimum", rupeeVolume, spreadPct, avgVolume);
        }
        if (spreadPct > cfg.getMaxSpreadPct()) {
            return new LiquidityDecision(false, "Spread too wide", rupeeVolume, spreadPct, avgVolume);
        }
        return new LiquidityDecision(true, "Liquidity ok", rupeeVolume, spreadPct, avgVolume);
    }

    private double averageVolume(List<Candle> candles, int period) {
        int start = Math.max(0, candles.size() - period);
        long sum = 0L;
        for (int i = start; i < candles.size(); i++) {
            sum += candles.get(i).getVolume();
        }
        int count = candles.size() - start;
        return count == 0 ? 0.0 : ((double) sum) / count;
    }

    private double proxySpreadPct(List<Candle> candles) {
        Candle last = candles.get(candles.size() - 1);
        double close = last.getClose();
        if (close <= 0) {
            return 0.0;
        }
        double raw = (last.getHigh() - last.getLow()) / close;
        double clamped = Math.max(0.0, Math.min(0.05, raw));
        return clamped * 100.0;
    }

    private String sanitizeSymbol(String symbol) {
        if (symbol == null) {
            return null;
        }
        String trimmed = symbol.trim().toUpperCase();
        if (trimmed.isBlank() || !trimmed.matches("[A-Z0-9:._-]+")) {
            return null;
        }
        return trimmed;
    }
}
