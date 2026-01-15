package com.apex.backend.service.indicator;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Candle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VolShockServiceTest {

    @Test
    void detectsShockAndCooldown() {
        StrategyProperties props = new StrategyProperties();
        props.getVolShock().setEnabled(true);
        props.getVolShock().setCooldownBars(2);
        VolShockService service = new VolShockService(props);

        List<Candle> candles = buildCandlesWithSpike(40);
        Instant now = Instant.now();

        VolShockService.VolShockDecision shock = service.evaluate("NSE:ABC", candles, 20, 1.5, now);
        assertThat(shock.shocked()).isTrue();

        VolShockService.VolShockDecision cooldown = service.evaluate("NSE:ABC", candles, 20, 1.5, now.plusSeconds(60));
        assertThat(cooldown.shocked()).isTrue();
        assertThat(cooldown.reason()).isEqualTo("Cooldown active");
    }

    private List<Candle> buildCandlesWithSpike(int count) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now().minusMinutes(count * 5L);
        for (int i = 0; i < count - 1; i++) {
            candles.add(new Candle(100, 101, 99, 100, 1000L, time.plusMinutes(i * 5L)));
        }
        candles.add(new Candle(100, 130, 70, 120, 1500L, time.plusMinutes((count - 1) * 5L)));
        return candles;
    }
}
