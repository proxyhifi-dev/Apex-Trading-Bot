package com.apex.backend.service.indicator;

import com.apex.backend.dto.PatternResult;
import com.apex.backend.model.Candle;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandlePatternDetectorTest {

    @Test
    void detectsEngulfingPattern() {
        Candle prev = new Candle(100, 101, 98, 99, 1000, LocalDateTime.now().minusMinutes(5));
        Candle curr = new Candle(98, 103, 97, 104, 1100, LocalDateTime.now());
        CandlePatternDetector detector = new CandlePatternDetector();

        PatternResult result = detector.detect(List.of(prev, curr));
        assertThat(result.type()).isEqualTo("ENGULFING");
        assertThat(result.bullish()).isTrue();
        assertThat(result.strengthScore()).isBetween(0.0, 1.0);
    }
}
