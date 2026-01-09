package com.apex.backend.service.indicator;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdxService {

    private final StrategyProperties strategyProperties;

    public AdxResult calculate(List<Candle> candles) {
        if (candles == null || candles.size() < 2) {
            return new AdxResult(0, 0, 0);
        }
        int period = strategyProperties.getAdx().getPeriod();
        if (candles.size() < period + 1) {
            return new AdxResult(0, 0, 0);
        }

        List<Double> tr = new ArrayList<>();
        List<Double> dmPlus = new ArrayList<>();
        List<Double> dmMinus = new ArrayList<>();

        for (int i = 1; i < candles.size(); i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);
            double highDiff = curr.getHigh() - prev.getHigh();
            double lowDiff = prev.getLow() - curr.getLow();
            double trueRange = Math.max(curr.getHigh() - curr.getLow(),
                    Math.max(Math.abs(curr.getHigh() - prev.getClose()), Math.abs(curr.getLow() - prev.getClose())));
            tr.add(trueRange);
            dmPlus.add((highDiff > lowDiff && highDiff > 0) ? highDiff : 0.0);
            dmMinus.add((lowDiff > highDiff && lowDiff > 0) ? lowDiff : 0.0);
        }

        double smoothTR = tr.subList(0, period).stream().mapToDouble(Double::doubleValue).sum();
        double smoothPlus = dmPlus.subList(0, period).stream().mapToDouble(Double::doubleValue).sum();
        double smoothMinus = dmMinus.subList(0, period).stream().mapToDouble(Double::doubleValue).sum();

        List<Double> dxValues = new ArrayList<>();
        double plusDI = 0.0;
        double minusDI = 0.0;

        for (int i = period - 1; i < tr.size(); i++) {
            if (i > period - 1) {
                smoothTR = smoothTR - (smoothTR / period) + tr.get(i);
                smoothPlus = smoothPlus - (smoothPlus / period) + dmPlus.get(i);
                smoothMinus = smoothMinus - (smoothMinus / period) + dmMinus.get(i);
            }

            if (smoothTR == 0) {
                continue;
            }
            plusDI = 100.0 * (smoothPlus / smoothTR);
            minusDI = 100.0 * (smoothMinus / smoothTR);
            double diSum = plusDI + minusDI;
            double dx = diSum == 0 ? 0.0 : (Math.abs(plusDI - minusDI) / diSum) * 100.0;
            dxValues.add(dx);
        }

        if (dxValues.size() < period) {
            return new AdxResult(0, plusDI, minusDI);
        }

        double adx = dxValues.subList(0, period).stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        for (int i = period; i < dxValues.size(); i++) {
            adx = ((adx * (period - 1)) + dxValues.get(i)) / period;
        }

        return new AdxResult(adx, plusDI, minusDI);
    }

    public record AdxResult(double adx, double plusDI, double minusDI) {}
}
