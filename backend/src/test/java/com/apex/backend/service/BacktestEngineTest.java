package com.apex.backend.service;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.config.StrategyProperties;
import com.apex.backend.repository.BacktestResultRepository;
import com.apex.backend.service.indicator.AtrService;
import com.apex.backend.service.indicator.CandleConfirmationValidator;
import com.apex.backend.service.indicator.MacdConfirmationService;
import com.apex.backend.service.indicator.MacdService;
import com.apex.backend.util.TestCandleFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BacktestEngineTest {

    @Test
    void goldenDatasetProducesStableMetrics() {
        StrategyProperties strategyProperties = new StrategyProperties();
        AdvancedTradingProperties advanced = new AdvancedTradingProperties();
        AtrService atrService = new AtrService(strategyProperties);
        MacdService macdService = new MacdService(strategyProperties);
        MacdConfirmationService macdConfirmationService = new MacdConfirmationService(macdService, advanced);
        CandleConfirmationValidator candleConfirmationValidator = new CandleConfirmationValidator(advanced);
        BacktestResultRepository repo = mock(BacktestResultRepository.class);
        BacktestEngine engine = new BacktestEngine(atrService, strategyProperties, advanced, macdConfirmationService, candleConfirmationValidator, repo);

        List<com.apex.backend.model.Candle> candles = TestCandleFactory.trendingCandles(120, 100, 0.8);
        Map<String, Object> metrics = engine.calculateMetrics(candles);

        assertThat(metrics.get("totalTrades")).isNotNull();
        assertThat((double) metrics.getOrDefault("winRate", 0.0)).isBetween(0.0, 1.0);
    }
}
