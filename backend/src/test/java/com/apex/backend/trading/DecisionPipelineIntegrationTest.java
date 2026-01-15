package com.apex.backend.trading;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.config.StrategyConfig;
import com.apex.backend.config.StrategyProperties;
import com.apex.backend.entity.SystemGuardState;
import com.apex.backend.model.Candle;
import com.apex.backend.repository.DecisionAuditRepository;
import com.apex.backend.service.DecisionAuditService;
import com.apex.backend.service.LiquidityGateService;
import com.apex.backend.service.MarketGateService;
import com.apex.backend.service.SmartSignalGenerator;
import com.apex.backend.service.SystemGuardService;
import com.apex.backend.service.TradingWindowService;
import com.apex.backend.service.indicator.AdxService;
import com.apex.backend.service.indicator.AtrService;
import com.apex.backend.service.indicator.BollingerBandService;
import com.apex.backend.service.indicator.CandleConfirmationValidator;
import com.apex.backend.service.indicator.CandlePatternDetector;
import com.apex.backend.service.indicator.ChoppinessIndexService;
import com.apex.backend.service.indicator.DonchianChannelService;
import com.apex.backend.service.indicator.MacdConfirmationService;
import com.apex.backend.service.indicator.MacdService;
import com.apex.backend.service.indicator.MultiTimeframeMomentumService;
import com.apex.backend.service.indicator.RsiService;
import com.apex.backend.service.indicator.SqueezeService;
import com.apex.backend.service.indicator.VolShockService;
import com.apex.backend.trading.pipeline.MarketDataProvider;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DecisionPipelineIntegrationTest {

    @Test
    void gatesBlockLiquidityBeforeSignal() {
        StrategyProperties strategyProperties = new StrategyProperties();
        strategyProperties.getMarketGate().setEnabled(true);
        strategyProperties.getTradingWindow().setEnabled(false);

        AdvancedTradingProperties advancedTradingProperties = new AdvancedTradingProperties();
        advancedTradingProperties.getLiquidity().setGateEnabled(true);
        advancedTradingProperties.getMarketRegime().setChopFilterEnabled(true);

        StrategyConfig strategyConfig = new StrategyConfig();
        strategyConfig.getTrading().setOwnerUserId(1L);

        MarketDataProvider marketDataProvider = mock(MarketDataProvider.class);
        when(marketDataProvider.getCandles(strategyProperties.getMarketGate().getIndexSymbol(), "D", 220))
                .thenReturn(buildDailyCandles(220));

        MarketGateService marketGateService = new MarketGateService(strategyProperties, marketDataProvider);
        LiquidityGateService liquidityGateService = new LiquidityGateService(advancedTradingProperties);
        TradingWindowService tradingWindowService = new TradingWindowService(strategyProperties);
        VolShockService volShockService = new VolShockService(strategyProperties);

        MacdService macdService = new MacdService(strategyProperties);
        AdxService adxService = new AdxService(strategyProperties);
        RsiService rsiService = new RsiService(strategyProperties);
        AtrService atrService = new AtrService(strategyProperties);
        BollingerBandService bollingerBandService = new BollingerBandService(strategyProperties);
        SqueezeService squeezeService = new SqueezeService(strategyProperties);
        com.apex.backend.service.StrategyScoringService scoringService = new com.apex.backend.service.StrategyScoringService(
                strategyProperties, macdService, adxService, rsiService, atrService, squeezeService);
        MacdConfirmationService macdConfirmationService = new MacdConfirmationService(macdService, advancedTradingProperties);
        CandleConfirmationValidator candleConfirmationValidator = new CandleConfirmationValidator(advancedTradingProperties);
        CandlePatternDetector candlePatternDetector = new CandlePatternDetector();
        MultiTimeframeMomentumService multiTfService = new MultiTimeframeMomentumService(advancedTradingProperties);
        DecisionAuditService decisionAuditService = new DecisionAuditService(mock(DecisionAuditRepository.class), advancedTradingProperties);
        com.apex.backend.service.risk.CircuitBreakerService circuitBreakerService = mock(com.apex.backend.service.risk.CircuitBreakerService.class);
        when(circuitBreakerService.canTrade(1L, org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.apex.backend.service.risk.CircuitBreakerService.GuardDecision(true, "ok", null));

        SmartSignalGenerator signalGenerator = new SmartSignalGenerator(
                strategyConfig,
                strategyProperties,
                advancedTradingProperties,
                macdService,
                adxService,
                rsiService,
                atrService,
                bollingerBandService,
                squeezeService,
                scoringService,
                macdConfirmationService,
                candleConfirmationValidator,
                candlePatternDetector,
                multiTfService,
                decisionAuditService,
                new ChoppinessIndexService(),
                new DonchianChannelService(),
                tradingWindowService,
                circuitBreakerService,
                allowSystemGuard(),
                marketGateService,
                liquidityGateService,
                volShockService
        );

        List<Candle> candles = buildLowLiquidityCandles(60);
        SmartSignalGenerator.SignalDecision decision = signalGenerator.generateSignalSmart(
                "NSE:TEST",
                candles,
                candles,
                candles,
                candles
        );

        assertThat(decision.isHasSignal()).isFalse();
        assertThat(decision.getReason()).contains("Liquidity gate");
    }

    private SystemGuardService allowSystemGuard() {
        SystemGuardService systemGuardService = mock(SystemGuardService.class);
        when(systemGuardService.getState()).thenReturn(SystemGuardState.builder().safeMode(false).build());
        return systemGuardService;
    }

    private List<Candle> buildLowLiquidityCandles(int count) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now().minusMinutes(count * 5L);
        double price = 100.0;
        for (int i = 0; i < count; i++) {
            candles.add(new Candle(price, price + 1, price - 1, price + 0.2, 100L, time.plusMinutes(i * 5L)));
            price += 0.1;
        }
        return candles;
    }

    private List<Candle> buildDailyCandles(int count) {
        List<Candle> candles = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now().minusDays(count);
        double price = 100.0;
        for (int i = 0; i < count; i++) {
            double close = price + 0.5;
            candles.add(new Candle(price, close + 0.5, price - 0.5, close, 1000L, time.plusDays(i)));
            price = close;
        }
        return candles;
    }
}
