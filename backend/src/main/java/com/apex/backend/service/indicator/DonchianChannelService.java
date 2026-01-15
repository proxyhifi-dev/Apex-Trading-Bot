package com.apex.backend.service.indicator;

import com.apex.backend.model.Candle;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DonchianChannelService {

    public record Donchian(int period, double upper, double lower) {}

    public Donchian calculate(List<Candle> candles, int period) {
        if (candles == null || candles.size() < 2) {
            return new Donchian(period, 0.0, 0.0);
        }
        int endExclusive = candles.size() - 1;
        int startIndex = Math.max(0, endExclusive - period);
        double upper = Double.NEGATIVE_INFINITY;
        double lower = Double.POSITIVE_INFINITY;
        for (int i = startIndex; i < endExclusive; i++) {
            Candle candle = candles.get(i);
            upper = Math.max(upper, candle.getHigh());
            lower = Math.min(lower, candle.getLow());
        }
        if (upper == Double.NEGATIVE_INFINITY || lower == Double.POSITIVE_INFINITY) {
            double lastClose = candles.get(candles.size() - 1).getClose();
            return new Donchian(period, lastClose, lastClose);
        }
        return new Donchian(period, upper, lower);
    }
}
