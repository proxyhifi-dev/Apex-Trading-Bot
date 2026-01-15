package com.apex.backend.service;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Candle;
import com.apex.backend.trading.pipeline.MarketDataProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketGateServiceTest {

    @Test
    void allowsWhenTrendSupportive() {
        StrategyProperties props = new StrategyProperties();
        props.getMarketGate().setEnabled(true);
        MarketDataProvider provider = mock(MarketDataProvider.class);
        List<Candle> candles = buildCandles(LocalDateTime.now().minusDays(10), 220, 100, 1.0);
        when(provider.getCandles(props.getMarketGate().getIndexSymbol(), "D", 220)).thenReturn(candles);

        MarketGateService service = new MarketGateService(props, provider);
        MarketGateService.MarketGateDecision decision = service.evaluateForLong(Instant.now());

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void blocksWhenDataStale() {
        StrategyProperties props = new StrategyProperties();
        props.getMarketGate().setEnabled(true);
        props.getMarketGate().setMaxStaleSeconds(60);
        MarketDataProvider provider = mock(MarketDataProvider.class);
        List<Candle> candles = buildCandles(LocalDateTime.now().minusDays(10), 220, 100, 1.0);
        Candle last = candles.get(candles.size() - 1);
        last.setTimestamp(LocalDateTime.now().minusHours(2));
        when(provider.getCandles(props.getMarketGate().getIndexSymbol(), "D", 220)).thenReturn(candles);

        MarketGateService service = new MarketGateService(props, provider);
        MarketGateService.MarketGateDecision decision = service.evaluateForLong(Instant.now());

        assertThat(decision.allowed()).isFalse();
    }

    private List<Candle> buildCandles(LocalDateTime start, int count, double base, double step) {
        List<Candle> candles = new java.util.ArrayList<>();
        LocalDateTime time = start;
        double price = base;
        for (int i = 0; i < count; i++) {
            double close = price + step;
            candles.add(new Candle(price, close + 0.5, price - 0.5, close, 1000L, time));
            price = close;
            time = time.plusDays(1);
        }
        return candles;
    }
}
