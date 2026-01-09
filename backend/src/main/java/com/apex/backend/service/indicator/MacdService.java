package com.apex.backend.service.indicator;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MacdService {

    private final StrategyProperties strategyProperties;

    public MacdResult calculate(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return new MacdResult(0, 0, 0, 0);
        }
        StrategyProperties.Macd config = strategyProperties.getMacd();
        int fast = config.getFastPeriod();
        int slow = config.getSlowPeriod();
        int signal = config.getSignalPeriod();

        List<Double> closes = candles.stream().map(Candle::getClose).toList();
        List<Double> fastSeries = calculateEmaSeries(closes, fast);
        List<Double> slowSeries = calculateEmaSeries(closes, slow);

        List<Double> macdSeries = new ArrayList<>();
        for (int i = 0; i < closes.size(); i++) {
            Double fastVal = fastSeries.get(i);
            Double slowVal = slowSeries.get(i);
            if (fastVal != null && slowVal != null) {
                macdSeries.add(fastVal - slowVal);
            }
        }

        if (macdSeries.size() < signal) {
            return new MacdResult(0, 0, 0, 0);
        }

        List<Double> signalSeries = calculateEmaSeries(macdSeries, signal);
        double macdLine = macdSeries.get(macdSeries.size() - 1);
        double signalLine = signalSeries.get(signalSeries.size() - 1);
        double histogram = macdLine - signalLine;
        double lastClose = closes.get(closes.size() - 1);
        double momentumScore = calculateMomentumScore(histogram, lastClose);
        return new MacdResult(macdLine, signalLine, histogram, momentumScore);
    }

    private double calculateMomentumScore(double histogram, double price) {
        if (price <= 0) {
            return 0;
        }
        double momentumPercent = Math.abs(histogram) / price * 100.0;
        return Math.min(11.0, Math.max(0.0, momentumPercent * 10.0));
    }

    private List<Double> calculateEmaSeries(List<Double> values, int period) {
        List<Double> emaSeries = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            emaSeries.add(null);
        }
        if (values.size() < period) {
            return emaSeries;
        }
        double sma = values.subList(0, period).stream().mapToDouble(d -> d).average().orElse(0.0);
        emaSeries.set(period - 1, sma);
        double k = 2.0 / (period + 1);
        double ema = sma;
        for (int i = period; i < values.size(); i++) {
            ema = (values.get(i) * k) + (ema * (1 - k));
            emaSeries.set(i, ema);
        }
        return emaSeries;
    }

    public record MacdResult(double macdLine, double signalLine, double histogram, double momentumScore) {}
}
