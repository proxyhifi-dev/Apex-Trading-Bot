package com.apex.backend.service.indicator;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.config.StrategyProperties;
import com.apex.backend.dto.MacdConfirmationDto;
import com.apex.backend.util.TestCandleFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MacdConfirmationServiceTest {

    @Test
    void detectsBullishCrossOnTrendingData() {
        StrategyProperties strategyProperties = new StrategyProperties();
        AdvancedTradingProperties advanced = new AdvancedTradingProperties();
        MacdService macdService = new MacdService(strategyProperties);
        MacdConfirmationService service = new MacdConfirmationService(macdService, advanced);
        List<com.apex.backend.model.Candle> candles = TestCandleFactory.trendingCandles(60, 100, 1.0);

        MacdConfirmationDto dto = service.confirm(candles);
        assertThat(dto.macdLine()).isNotZero();
        assertThat(dto.histogramIncreasing() || dto.bullishCrossover() || dto.zeroLineCrossUp()).isTrue();
    }
}
