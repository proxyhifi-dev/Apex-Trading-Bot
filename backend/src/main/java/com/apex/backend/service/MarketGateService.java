package com.apex.backend.service;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Candle;
import com.apex.backend.trading.pipeline.MarketDataProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Service
public class MarketGateService {

    private final StrategyProperties strategyProperties;
    private final MarketDataProvider marketDataProvider;

    public MarketGateService(StrategyProperties strategyProperties, MarketDataProvider marketDataProvider) {
        this.strategyProperties = strategyProperties;
        this.marketDataProvider = marketDataProvider;
    }

    public record MarketGateDecision(boolean allowed, String reason, double emaFast, double emaSlow, double lastClose) {}

    public MarketGateDecision evaluateForLong(Instant nowUtc) {
        StrategyProperties.MarketGate config = strategyProperties.getMarketGate();
        if (!config.isEnabled()) {
            return new MarketGateDecision(true, "Market gate disabled", 0.0, 0.0, 0.0);
        }
        String indexSymbol = sanitizeSymbol(config.getIndexSymbol());
        if (indexSymbol == null) {
            return new MarketGateDecision(false, "Invalid index symbol", 0.0, 0.0, 0.0);
        }
        int bars = Math.max(config.getEmaSlow() + 5, 220);
        List<Candle> candles = marketDataProvider.getCandles(indexSymbol, "D", bars);
        if (candles == null || candles.size() < config.getEmaSlow()) {
            return new MarketGateDecision(false, "Insufficient index data", 0.0, 0.0, 0.0);
        }
        Candle last = candles.get(candles.size() - 1);
        Instant lastCandleTime = last.getTimestamp().atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        if (config.isBlockIfIndexDataStale()) {
            Duration age = Duration.between(lastCandleTime, nowUtc);
            if (age.getSeconds() > config.getMaxStaleSeconds()) {
                return new MarketGateDecision(false, "Index data stale", 0.0, 0.0, last.getClose());
            }
        }
        double emaFast = calculateEma(candles, config.getEmaFast());
        double emaSlow = calculateEma(candles, config.getEmaSlow());
        double lastClose = last.getClose();
        boolean trendOk = emaFast > emaSlow;
        boolean priceOk = !config.isRequirePriceAboveFastEma() || lastClose > emaFast;
        if (!trendOk || !priceOk) {
            return new MarketGateDecision(false, "Index trend not supportive", emaFast, emaSlow, lastClose);
        }
        return new MarketGateDecision(true, "Index trend supportive", emaFast, emaSlow, lastClose);
    }

    private double calculateEma(List<Candle> candles, int period) {
        if (candles.size() < period) {
            return 0.0;
        }
        double multiplier = 2.0 / (period + 1.0);
        double ema = candles.subList(0, period).stream().mapToDouble(Candle::getClose).average().orElse(0.0);
        for (int i = period; i < candles.size(); i++) {
            double close = candles.get(i).getClose();
            ema = ((close - ema) * multiplier) + ema;
        }
        return ema;
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
