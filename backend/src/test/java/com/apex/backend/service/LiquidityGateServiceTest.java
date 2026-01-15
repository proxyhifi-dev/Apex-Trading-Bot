package com.apex.backend.service;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.model.Candle;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LiquidityGateServiceTest {

    @Test
    void blocksOnWideSpread() {
        AdvancedTradingProperties props = new AdvancedTradingProperties();
        props.getLiquidity().setGateEnabled(true);
        LiquidityGateService service = new LiquidityGateService(props);

        List<Candle> candles = buildCandles(25, 100, 20);
        LiquidityGateService.LiquidityDecision decision = service.evaluate("NSE:ABC", candles, 100);

        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void allowsWithHealthyVolumeAndSpread() {
        AdvancedTradingProperties props = new AdvancedTradingProperties();
        props.getLiquidity().setGateEnabled(true);
        LiquidityGateService service = new LiquidityGateService(props);

        List<Candle> candles = buildCandles(25, 100, 1);
        LiquidityGateService.LiquidityDecision decision = service.evaluate("NSE:ABC", candles, 100);

        assertThat(decision.allowed()).isTrue();
    }

    private List<Candle> buildCandles(int count, double close, double range) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now().minusMinutes(count * 5L);
        for (int i = 0; i < count; i++) {
            candles.add(new Candle(close, close + range, close - range, close, 200000L, time.plusMinutes(i * 5L)));
        }
        return candles;
    }
}
