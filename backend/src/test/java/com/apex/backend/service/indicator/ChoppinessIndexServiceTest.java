package com.apex.backend.service.indicator;

import com.apex.backend.util.TestCandleFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChoppinessIndexServiceTest {

    @Test
    void choppinessHigherInRange() {
        ChoppinessIndexService service = new ChoppinessIndexService();
        List<com.apex.backend.model.Candle> trending = TestCandleFactory.trendingCandles(60, 100, 1.0);
        List<com.apex.backend.model.Candle> range = TestCandleFactory.oscillatingCandles(60, 100, 0.5);

        double chopTrend = service.calculate(trending, 14).chop();
        double chopRange = service.calculate(range, 14).chop();

        assertThat(chopRange).isGreaterThan(chopTrend);
    }

    @Test
    void returnsMaxWhenInsufficientData() {
        ChoppinessIndexService service = new ChoppinessIndexService();
        List<com.apex.backend.model.Candle> candles = TestCandleFactory.trendingCandles(10, 100, 1.0);

        assertThat(service.calculate(candles, 14).chop()).isEqualTo(100.0);
    }
}
