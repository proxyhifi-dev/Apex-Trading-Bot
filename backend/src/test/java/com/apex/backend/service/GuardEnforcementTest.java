package com.apex.backend.service;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.config.StrategyProperties;
import com.apex.backend.entity.SystemGuardState;
import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.trading.pipeline.DecisionResult;
import com.apex.backend.trading.pipeline.RiskDecision;
import com.apex.backend.trading.pipeline.SignalScore;
import com.apex.backend.trading.pipeline.TradeDecisionPipelineService;
import com.apex.backend.service.indicator.VolShockService;
import com.apex.backend.util.TestCandleFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuardEnforcementTest {

    @Test
    void safeModeBlocksNewOrders() {
        FyersService fyersService = mock(FyersService.class);
        TradeRepository tradeRepository = mock(TradeRepository.class);
        StockScreeningResultRepository screeningRepo = mock(StockScreeningResultRepository.class);
        RiskManagementEngine riskEngine = mock(RiskManagementEngine.class);
        PortfolioService portfolioService = mock(PortfolioService.class);
        DeadLetterQueueService dlqService = mock(DeadLetterQueueService.class);
        PaperTradingService paperTradingService = mock(PaperTradingService.class);
        SettingsService settingsService = mock(SettingsService.class);
        StrategyProperties strategyProperties = new StrategyProperties();
        AdvancedTradingProperties advancedTradingProperties = new AdvancedTradingProperties();
        HybridPositionSizingService hybridPositionSizingService = mock(HybridPositionSizingService.class);
        MetricsService metricsService = mock(MetricsService.class);
        DecisionAuditService decisionAuditService = mock(DecisionAuditService.class);
        TradeDecisionPipelineService pipelineService = mock(TradeDecisionPipelineService.class);
        TradeFeatureAttributionService attributionService = mock(TradeFeatureAttributionService.class);
        ExecutionEngine executionEngine = mock(ExecutionEngine.class);
        StopLossPlacementService stopLossPlacementService = mock(StopLossPlacementService.class);
        FyersAuthService fyersAuthService = mock(FyersAuthService.class);
        CircuitBreakerService circuitBreakerService = mock(CircuitBreakerService.class);
        AlertService alertService = mock(AlertService.class);
        com.apex.backend.service.risk.CircuitBreakerService tradingGuardService = mock(com.apex.backend.service.risk.CircuitBreakerService.class);
        SystemGuardService systemGuardService = mock(SystemGuardService.class);
        TradingWindowService tradingWindowService = mock(TradingWindowService.class);
        MarketGateService marketGateService = mock(MarketGateService.class);
        LiquidityGateService liquidityGateService = mock(LiquidityGateService.class);
        VolShockService volShockService = mock(VolShockService.class);
        BroadcastService broadcastService = mock(BroadcastService.class);

        TradeExecutionService service = new TradeExecutionService(
                fyersService,
                tradeRepository,
                screeningRepo,
                riskEngine,
                portfolioService,
                dlqService,
                paperTradingService,
                settingsService,
                strategyProperties,
                advancedTradingProperties,
                hybridPositionSizingService,
                metricsService,
                decisionAuditService,
                pipelineService,
                attributionService,
                executionEngine,
                stopLossPlacementService,
                fyersAuthService,
                circuitBreakerService,
                alertService,
                tradingGuardService,
                systemGuardService,
                tradingWindowService,
                marketGateService,
                liquidityGateService,
                volShockService,
                broadcastService
        );

        StockScreeningResult signal = StockScreeningResult.builder()
                .id(1L)
                .userId(1L)
                .symbol("NSE:ABC")
                .approvalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                .entryPrice(java.math.BigDecimal.valueOf(100))
                .stopLoss(java.math.BigDecimal.valueOf(95))
                .analysisReason("reason")
                .scanTime(LocalDateTime.now())
                .build();

        when(screeningRepo.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(signal));
        when(settingsService.isPaperModeForUser(1L)).thenReturn(true);
        when(fyersService.getHistoricalData("NSE:ABC", 200, "5")).thenReturn(TestCandleFactory.trendingCandles(60, 100, 1.0));
        SignalScore score = new SignalScore(true, 80.0, "A", 100.0, 95.0, "ok", null, List.of());
        DecisionResult decision = new DecisionResult("NSE:ABC", DecisionResult.DecisionAction.BUY, 80.0, List.of(), new RiskDecision(true, 1.0, List.of(), 1.0, 10), null, score, null);
        when(pipelineService.evaluate(any())).thenReturn(decision);

        SystemGuardState state = SystemGuardState.builder().id(1L).safeMode(true).build();
        when(systemGuardService.getState()).thenReturn(state);

        service.approveAndExecute(1L, 1L, true, 15.0);

        verify(executionEngine, never()).execute(any());
        verify(metricsService).recordReject("SAFE_MODE");
    }
}
