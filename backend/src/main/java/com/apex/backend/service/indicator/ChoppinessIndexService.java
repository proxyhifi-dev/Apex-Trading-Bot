package com.apex.backend.service.indicator;

import com.apex.backend.model.Candle;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChoppinessIndexService {

    public record ChopResult(double chop, int period) {}

    public ChopResult calculate(List<Candle> candles, int period) {
        if (candles == null || candles.size() < period + 2) {
            return new ChopResult(100.0, period);
        }
        int endIndex = candles.size() - 1;
        double highestHigh = Double.NEGATIVE_INFINITY;
        double lowestLow = Double.POSITIVE_INFINITY;
        double trSum = 0.0;

        int startIndex = endIndex - period;
        for (int i = startIndex; i <= endIndex; i++) {
            Candle current = candles.get(i);
            Candle prev = candles.get(i - 1);
            double tr = Math.max(current.getHigh() - current.getLow(),
                    Math.max(Math.abs(current.getHigh() - prev.getClose()), Math.abs(current.getLow() - prev.getClose())));
            trSum += tr;
            highestHigh = Math.max(highestHigh, current.getHigh());
            lowestLow = Math.min(lowestLow, current.getLow());
        }

        double range = highestHigh - lowestLow;
        if (range <= 0) {
            return new ChopResult(100.0, period);
        }
        double atr = trSum / period;
        double sumAtr = atr * period;
        double chop = 100.0 * (Math.log10(sumAtr / range) / Math.log10(period));
        if (Double.isNaN(chop) || Double.isInfinite(chop)) {
            return new ChopResult(100.0, period);
        }
        return new ChopResult(chop, period);
    }
}
