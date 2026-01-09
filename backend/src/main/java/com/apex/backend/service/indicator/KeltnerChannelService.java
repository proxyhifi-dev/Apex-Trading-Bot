package com.apex.backend.service.indicator;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KeltnerChannelService {

    private final StrategyProperties strategyProperties;
    private final AtrService atrService;

    public KeltnerChannel calculate(List<Candle> candles, int endIndex) {
        int period = strategyProperties.getKeltner().getPeriod();
        if (candles == null || candles.size() < period || endIndex < period - 1) {
            return new KeltnerChannel(0, 0, 0, 0);
        }
        List<Candle> window = candles.subList(endIndex - period + 1, endIndex + 1);
        double middle = calculateEma(window.stream().map(Candle::getClose).toList(), period);
        double atr = atrService.calculate(candles.subList(0, endIndex + 1)).atr();
        double multiplier = strategyProperties.getKeltner().getAtrMultiplier();
        double upper = middle + (atr * multiplier);
        double lower = middle - (atr * multiplier);
        double width = upper - lower;
        return new KeltnerChannel(upper, middle, lower, width);
    }

    public KeltnerChannel calculate(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return new KeltnerChannel(0, 0, 0, 0);
        }
        return calculate(candles, candles.size() - 1);
    }

    private double calculateEma(List<Double> values, int period) {
        if (values.size() < period) {
            return 0.0;
        }
        double sma = values.subList(0, period).stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double k = 2.0 / (period + 1);
        double ema = sma;
        for (int i = period; i < values.size(); i++) {
            ema = (values.get(i) * k) + (ema * (1 - k));
        }
        return ema;
    }

    public record KeltnerChannel(double upper, double middle, double lower, double width) {}
}
