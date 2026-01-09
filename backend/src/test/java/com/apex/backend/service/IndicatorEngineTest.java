package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.model.Candle;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndicatorEngineTest {

    @Test
    void calculatesEmaWithSmaSeed() {
        StrategyConfig config = new StrategyConfig();
        IndicatorEngine engine = new IndicatorEngine(config);
        List<Candle> candles = buildCandles(10, 11, 12, 13, 14);

        double ema = engine.calculateEMA(candles, 3);

        assertEquals(13.0, ema, 1e-6);
    }

    @Test
    void calculatesRsiWithWilderSmoothing() {
        StrategyConfig config = new StrategyConfig();
        config.getStrategy().setRsiPeriod(5);
        IndicatorEngine engine = new IndicatorEngine(config);
        List<Candle> candles = buildCandles(10, 11, 12, 13, 14, 15, 16);

        double rsi = engine.calculateRSI(candles);

        assertTrue(rsi > 99.0);
    }

    @Test
    void calculatesMacdSeriesAndSignal() {
        StrategyConfig config = new StrategyConfig();
        config.getStrategy().setMacdFastPeriod(3);
        config.getStrategy().setMacdSlowPeriod(6);
        config.getStrategy().setMacdSignalPeriod(3);
        IndicatorEngine engine = new IndicatorEngine(config);
        List<Candle> candles = buildCandles(10, 11, 12, 13, 14, 15, 16, 17, 18, 19);

        IndicatorEngine.MacdResult result = engine.calculateMACD(candles);

        assertTrue(result.getMacdLine() > 0.0);
        assertTrue(result.getSignalLine() > 0.0);
        assertTrue(result.getMacdLine() > result.getSignalLine());
        assertEquals(result.getMacdLine() - result.getSignalLine(), result.getHistogram(), 1e-6);
    }

    @Test
    void calculatesAdxWithWilderSmoothing() {
        StrategyConfig config = new StrategyConfig();
        config.getStrategy().setAdxPeriod(3);
        IndicatorEngine engine = new IndicatorEngine(config);
        List<Candle> candles = buildCandles(10, 11, 12, 13, 14, 15, 16, 17);

        IndicatorEngine.AdxResult result = engine.calculateADX(candles);

        assertTrue(result.getAdx() >= 0.0 && result.getAdx() <= 100.0);
        assertTrue(result.getPlusDI() >= result.getMinusDI());
    }

    @Test
    void calculatesCorrelationOnReturnsWithScaleInvariance() {
        StrategyConfig config = new StrategyConfig();
        IndicatorEngine engine = new IndicatorEngine(config);
        List<Candle> base = buildCandles(100, 101, 99, 102, 103, 101, 104, 105);
        List<Candle> scaled = buildCandles(200, 202, 198, 204, 206, 202, 208, 210);

        double correlation = engine.calculateCorrelation(base, scaled);

        assertTrue(correlation > 0.99);
    }

    private List<Candle> buildCandles(double... closes) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now();
        for (int i = 0; i < closes.length; i++) {
            double close = closes[i];
            candles.add(Candle.builder()
                    .open(close)
                    .high(close + 1)
                    .low(Math.max(0.1, close - 1))
                    .close(close)
                    .volume(1000)
                    .timestamp(time.plusMinutes(i))
                    .build());
        }
        return candles;
    }
}
