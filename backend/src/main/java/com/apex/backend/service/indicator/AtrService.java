package com.apex.backend.service.indicator;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AtrService {

    private final StrategyProperties strategyProperties;

    public AtrResult calculate(List<Candle> candles) {
        if (candles == null || candles.size() < 2) {
            return new AtrResult(0, 0);
        }
        int period = strategyProperties.getAtr().getPeriod();
        double atr = calculateAtrWilder(candles, period);
        double lastClose = candles.get(candles.size() - 1).getClose();
        double atrPercent = lastClose <= 0 ? 0 : (atr / lastClose) * 100.0;
        return new AtrResult(atr, atrPercent);
    }

    private double calculateAtrWilder(List<Candle> candles, int period) {
        if (candles.size() < period + 1) {
            return 0.0;
        }
        List<Double> tr = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);
            tr.add(Math.max(curr.getHigh() - curr.getLow(),
                    Math.max(Math.abs(curr.getHigh() - prev.getClose()), Math.abs(curr.getLow() - prev.getClose()))));
        }
        if (tr.size() < period) {
            return 0.0;
        }
        double atr = tr.subList(0, period).stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        for (int i = period; i < tr.size(); i++) {
            atr = ((atr * (period - 1)) + tr.get(i)) / period;
        }
        return atr;
    }

    public record AtrResult(double atr, double atrPercent) {}
}
