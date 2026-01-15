package com.apex.backend.service.indicator;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Candle;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VolShockService {

    private static final int ATR_PERIOD = 14;
    private static final Duration DEFAULT_BAR_DURATION = Duration.ofMinutes(5);
    private final Map<String, Instant> cooldownUntilBySymbol = new ConcurrentHashMap<>();
    private final StrategyProperties strategyProperties;

    public VolShockService(StrategyProperties strategyProperties) {
        this.strategyProperties = strategyProperties;
    }

    public record VolShockDecision(boolean shocked, String reason, double atrPct, double medianAtrPct, int cooldownBarsRemaining) {}

    public VolShockDecision evaluate(String symbol, List<Candle> candles, int lookback, double multiplier, Instant nowUtc) {
        if (candles == null || candles.size() < ATR_PERIOD + 2) {
            return new VolShockDecision(false, "Insufficient data", 0.0, 0.0, 0);
        }
        String key = symbol == null ? "" : symbol.trim().toUpperCase();
        Instant cooldownUntil = cooldownUntilBySymbol.get(key);
        Duration barDuration = resolveBarDuration(candles);
        if (cooldownUntil != null && nowUtc.isBefore(cooldownUntil)) {
            int remaining = (int) Math.ceil((double) Duration.between(nowUtc, cooldownUntil).toSeconds() / Math.max(1, barDuration.toSeconds()));
            return new VolShockDecision(true, "Cooldown active", 0.0, 0.0, remaining);
        }

        AtrSeries atrSeries = computeAtrSeries(candles, ATR_PERIOD);
        double atr = atrSeries.latestAtr;
        double lastClose = candles.get(candles.size() - 1).getClose();
        double atrPct = lastClose <= 0 ? 0.0 : (atr / lastClose) * 100.0;
        double medianAtrPct = medianAtrPct(atrSeries.atrPercentSeries, lookback);
        boolean shocked = medianAtrPct > 0 && atrPct > (medianAtrPct * multiplier);
        if (shocked) {
            int cooldownBars = Math.max(1, strategyProperties.getVolShock().getCooldownBars());
            Instant until = nowUtc.plus(barDuration.multipliedBy(cooldownBars));
            cooldownUntilBySymbol.put(key, until);
            return new VolShockDecision(true, "ATR spike", atrPct, medianAtrPct, cooldownBars);
        }
        return new VolShockDecision(false, "No shock", atrPct, medianAtrPct, 0);
    }

    private Duration resolveBarDuration(List<Candle> candles) {
        if (candles.size() < 2) {
            return DEFAULT_BAR_DURATION;
        }
        Candle last = candles.get(candles.size() - 1);
        Candle prev = candles.get(candles.size() - 2);
        if (last.getTimestamp() == null || prev.getTimestamp() == null) {
            return DEFAULT_BAR_DURATION;
        }
        ZoneId zone = ZoneId.of(strategyProperties.getTradingWindow().getTimezone());
        Instant lastInstant = last.getTimestamp().atZone(zone).toInstant();
        Instant prevInstant = prev.getTimestamp().atZone(zone).toInstant();
        Duration duration = Duration.between(prevInstant, lastInstant);
        if (duration.isNegative() || duration.isZero()) {
            return DEFAULT_BAR_DURATION;
        }
        return duration;
    }

    private double medianAtrPct(List<Double> atrPctSeries, int lookback) {
        if (atrPctSeries.isEmpty()) {
            return 0.0;
        }
        int start = Math.max(0, atrPctSeries.size() - lookback);
        List<Double> subset = new ArrayList<>(atrPctSeries.subList(start, atrPctSeries.size()));
        Collections.sort(subset);
        int mid = subset.size() / 2;
        if (subset.size() % 2 == 0) {
            return (subset.get(mid - 1) + subset.get(mid)) / 2.0;
        }
        return subset.get(mid);
    }

    private AtrSeries computeAtrSeries(List<Candle> candles, int period) {
        int size = candles.size();
        double[] trValues = new double[size];
        for (int i = 1; i < size; i++) {
            Candle current = candles.get(i);
            Candle prev = candles.get(i - 1);
            double tr = Math.max(current.getHigh() - current.getLow(),
                    Math.max(Math.abs(current.getHigh() - prev.getClose()), Math.abs(current.getLow() - prev.getClose())));
            trValues[i] = tr;
        }
        double atr = 0.0;
        List<Double> atrPctSeries = new ArrayList<>();
        double sum = 0.0;
        for (int i = 1; i < size; i++) {
            sum += trValues[i];
            if (i == period) {
                atr = sum / period;
            } else if (i > period) {
                atr = ((atr * (period - 1)) + trValues[i]) / period;
            }
            if (i >= period) {
                double close = candles.get(i).getClose();
                double atrPct = close <= 0 ? 0.0 : (atr / close) * 100.0;
                atrPctSeries.add(atrPct);
            }
        }
        return new AtrSeries(atr, atrPctSeries);
    }

    private record AtrSeries(double latestAtr, List<Double> atrPercentSeries) {}
}
