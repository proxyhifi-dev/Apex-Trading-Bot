package com.apex.backend.service.indicator;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BollingerBandService {

    private final StrategyProperties strategyProperties;

    public BollingerBands calculate(List<Candle> candles, int endIndex) {
        int period = strategyProperties.getBollinger().getPeriod();
        if (candles == null || candles.size() < period || endIndex < period - 1) {
            return new BollingerBands(0, 0, 0, 0);
        }
        int startIndex = endIndex - period + 1;
        List<Candle> window = candles.subList(startIndex, endIndex + 1);
        double mean = window.stream().mapToDouble(Candle::getClose).average().orElse(0.0);
        double variance = window.stream()
                .mapToDouble(candle -> {
                    double diff = candle.getClose() - mean;
                    return diff * diff;
                })
                .average()
                .orElse(0.0);
        double standardDeviation = Math.sqrt(variance);
        double stdDevMultiplier = strategyProperties.getBollinger().getDeviation();
        double upper = mean + (standardDeviation * stdDevMultiplier);
        double lower = mean - (standardDeviation * stdDevMultiplier);
        double width = upper - lower;
        return new BollingerBands(upper, mean, lower, width);
    }

    public BollingerBands calculate(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return new BollingerBands(0, 0, 0, 0);
        }
        return calculate(candles, candles.size() - 1);
    }

    public record BollingerBands(double upper, double middle, double lower, double width) {}
}
