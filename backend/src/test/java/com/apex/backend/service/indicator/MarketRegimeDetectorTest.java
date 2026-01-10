package com.apex.backend.service.indicator;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.MarketRegime;
import com.apex.backend.repository.MarketRegimeHistoryRepository;
import com.apex.backend.service.DecisionAuditService;
import com.apex.backend.util.TestCandleFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MarketRegimeDetectorTest {

    @Test
    void detectsTrendingRegime() {
        StrategyProperties strategyProperties = new StrategyProperties();
        AdxService adxService = new AdxService(strategyProperties);
        AtrService atrService = new AtrService(strategyProperties);
        AdvancedTradingProperties advanced = new AdvancedTradingProperties();
        MarketRegimeHistoryRepository repo = mock(MarketRegimeHistoryRepository.class);
        DecisionAuditService auditService = mock(DecisionAuditService.class);
        MarketRegimeDetector detector = new MarketRegimeDetector(adxService, atrService, advanced, repo, auditService);

        List<com.apex.backend.model.Candle> candles = TestCandleFactory.trendingCandles(60, 100, 1.5);
        MarketRegime regime = detector.detectAndStore("TEST", "5m", candles);

        assertThat(regime).isIn(MarketRegime.TRENDING, MarketRegime.HIGH_VOL, MarketRegime.LOW_VOL);
    }
}
