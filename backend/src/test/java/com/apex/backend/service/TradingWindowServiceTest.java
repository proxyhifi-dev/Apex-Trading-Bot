package com.apex.backend.service;

import com.apex.backend.config.StrategyProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TradingWindowServiceTest {

    @Test
    void allowsInsideWindow() {
        StrategyProperties props = new StrategyProperties();
        props.getTradingWindow().setEnabled(true);
        props.getTradingWindow().setTimezone("Asia/Kolkata");
        props.getTradingWindow().setWindows(List.of("09:20-11:30"));
        TradingWindowService service = new TradingWindowService(props);

        Instant now = ZonedDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();
        TradingWindowService.WindowDecision decision = service.evaluate(now);

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void blocksOutsideWindow() {
        StrategyProperties props = new StrategyProperties();
        props.getTradingWindow().setEnabled(true);
        props.getTradingWindow().setTimezone("Asia/Kolkata");
        props.getTradingWindow().setWindows(List.of("09:20-11:30"));
        TradingWindowService service = new TradingWindowService(props);

        Instant now = ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();
        TradingWindowService.WindowDecision decision = service.evaluate(now);

        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void blocksBlackout() {
        StrategyProperties props = new StrategyProperties();
        props.getTradingWindow().setEnabled(true);
        props.getTradingWindow().setTimezone("Asia/Kolkata");
        props.getTradingWindow().setWindows(List.of("09:20-11:30"));
        props.getTradingWindow().setBlackout(List.of("10:00-10:30"));
        TradingWindowService service = new TradingWindowService(props);

        Instant now = ZonedDateTime.of(2024, 1, 1, 10, 15, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();
        TradingWindowService.WindowDecision decision = service.evaluate(now);

        assertThat(decision.allowed()).isFalse();
    }
}
