package com.apex.backend.util;

import com.apex.backend.model.Candle;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class TestCandleFactory {

    private TestCandleFactory() {}

    public static List<Candle> trendingCandles(int count, double start, double step) {
        List<Candle> candles = new ArrayList<>();
        double price = start;
        LocalDateTime time = LocalDateTime.now().minusMinutes(count * 5L);
        for (int i = 0; i < count; i++) {
            double open = price;
            double close = price + step;
            double high = Math.max(open, close) + step * 0.3;
            double low = Math.min(open, close) - step * 0.2;
            candles.add(new Candle(open, high, low, close, 1000L + i * 10L, time.plusMinutes(i * 5L)));
            price = close;
        }
        return candles;
    }

    public static List<Candle> oscillatingCandles(int count, double base, double amplitude) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now().minusMinutes(count * 5L);
        for (int i = 0; i < count; i++) {
            double offset = Math.sin(i / 3.0) * amplitude;
            double open = base + offset;
            double close = base - offset * 0.5;
            double high = Math.max(open, close) + amplitude * 0.5;
            double low = Math.min(open, close) - amplitude * 0.5;
            candles.add(new Candle(open, high, low, close, 900L + i * 5L, time.plusMinutes(i * 5L)));
        }
        return candles;
    }
}
