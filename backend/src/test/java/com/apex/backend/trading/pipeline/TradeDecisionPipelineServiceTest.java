package com.apex.backend.trading.pipeline;

import com.apex.backend.config.DataQualityProperties;
import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Candle;
import com.apex.backend.service.DataQualityGuard;
import com.apex.backend.service.FeatureAttributionService;
import com.apex.backend.service.SmartSignalGenerator;
import com.apex.backend.service.StrategyScoringService;
import com.apex.backend.util.TestCandleFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeDecisionPipelineServiceTest {

    @Test
    void pipelineProducesBuyDecisionWithMocks() {
        StrategyProperties strategyProperties = new StrategyProperties();
        FeatureAttributionService featureAttributionService = new FeatureAttributionService();

        MarketDataProvider marketDataProvider = mock(MarketDataProvider.class);
        SmartSignalGenerator smartSignalGenerator = mock(SmartSignalGenerator.class);
        StrategyScoringService strategyScoringService = mock(StrategyScoringService.class);

        List<Candle> candles = TestCandleFactory.trendingCandles(60, 100, 1.0);
        when(marketDataProvider.getCandles(anyString(), anyString(), any(Integer.class))).thenReturn(candles);

        SmartSignalGenerator.SignalDecision signalDecision = SmartSignalGenerator.SignalDecision.builder()
                .hasSignal(true)
                .symbol("TEST")
                .score(80)
                .grade("A")
                .entryPrice(100.0)
                .suggestedStopLoss(95.0)
                .reason("OK")
                .build();
        when(smartSignalGenerator.generateSignalSmart(anyString(), anyList(), anyList(), anyList(), anyList()))
                .thenReturn(signalDecision);

        StrategyScoringService.ScoreBreakdown breakdown = new StrategyScoringService.ScoreBreakdown(
                80.0,
                16.0,
                16.0,
                16.0,
                16.0,
                16.0,
                8.0,
                30.0,
                55.0,
                2.0,
                1.0,
                true,
                5,
                0.8
        );
        when(strategyScoringService.score(anyList())).thenReturn(breakdown);

        SignalEngine signalEngine = new DefaultSignalEngine(
                marketDataProvider,
                smartSignalGenerator,
                strategyScoringService,
                featureAttributionService,
                strategyProperties
        );

        RiskEngine riskEngine = (request, signalScore, snapshot) -> new RiskDecision(true, 1.0, List.of(), 1.0, 10);
        ExecutionEngine executionEngine = (request, signalScore, riskDecision) -> new ExecutionPlan(
                ExecutionPlan.ExecutionOrderType.MARKET,
                java.math.BigDecimal.valueOf(100.5),
                java.math.BigDecimal.valueOf(5.0),
                1.0,
                java.math.BigDecimal.valueOf(0.5),
                java.math.BigDecimal.valueOf(1.0),
                java.math.BigDecimal.valueOf(2.0),
                java.math.BigDecimal.valueOf(1.5)
        );
        PortfolioEngine portfolioEngine = request -> new PortfolioSnapshot(java.math.BigDecimal.valueOf(100000), 0.01, java.util.Map.of(), List.of());
        StrategyHealthEngine healthEngine = userId -> new StrategyHealthDecision(StrategyHealthDecision.StrategyHealthStatus.HEALTHY, List.of());
        DataQualityGuard dataQualityGuard = new DataQualityGuard(new DataQualityProperties());

        TradeDecisionPipelineService pipelineService = new TradeDecisionPipelineService(
                signalEngine,
                riskEngine,
                executionEngine,
                portfolioEngine,
                healthEngine,
                dataQualityGuard
        );

        DecisionResult result = pipelineService.evaluate(new PipelineRequest(
                1L,
                "TEST",
                "5",
                candles,
                null
        ));

        assertThat(result.action()).isEqualTo(DecisionResult.DecisionAction.BUY);
        assertThat(result.executionPlan()).isNotNull();
        assertThat(result.signalScore().featureContributions()).isNotEmpty();
    }
}
