package com.apex.backend.service;

import com.apex.backend.exception.BadRequestException;
import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.model.Trade;
import com.apex.backend.util.MoneyUtils;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.trading.pipeline.DecisionResult;
import com.apex.backend.trading.pipeline.PipelineRequest;
import com.apex.backend.trading.pipeline.TradeDecisionPipelineService;
import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.config.StrategyProperties;
import com.apex.backend.service.indicator.VolShockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TradeExecutionService {

    private final FyersService fyersService;
    private final TradeRepository tradeRepo;
    private final StockScreeningResultRepository screeningRepo;
    private final RiskManagementEngine riskEngine;
    private final PortfolioService portfolioService;
    private final DeadLetterQueueService dlqService;
    private final PaperTradingService paperTradingService;
    private final SettingsService settingsService;
    private final StrategyProperties strategyProperties;
    private final AdvancedTradingProperties advancedTradingProperties;
    private final HybridPositionSizingService hybridPositionSizingService;
    private final MetricsService metricsService;
    private final DecisionAuditService decisionAuditService;
    private final TradeDecisionPipelineService tradeDecisionPipelineService;
    private final TradeFeatureAttributionService tradeFeatureAttributionService;
    private final ExecutionEngine executionEngine;
    private final StopLossPlacementService stopLossPlacementService;
    private final FyersAuthService fyersAuthService;
    private final CircuitBreakerService circuitBreakerService;
    private final AlertService alertService;
    private final com.apex.backend.service.risk.CircuitBreakerService tradingGuardService;
    private final SystemGuardService systemGuardService;
    private final TradingWindowService tradingWindowService;
    private final MarketGateService marketGateService;
    private final LiquidityGateService liquidityGateService;
    private final VolShockService volShockService;
    private final BroadcastService broadcastService;

    @Transactional
    public void executeAutoTrade(Long userId, DecisionResult decision, boolean isPaper, double currentVix) {
        if (userId == null) {
            throw new BadRequestException("User ID is required for trade execution");
        }
        boolean effectivePaperMode = settingsService.isPaperModeForUser(userId);
        log.info("🤖 Auto-Trade Signal: {} | VIX: {} | Mode: {}", decision.symbol(), currentVix, effectivePaperMode ? "PAPER" : "LIVE");
        log.info("📊 Score Breakdown: {}", decision.signalScore().reason());

        StockScreeningResult signal = StockScreeningResult.builder()
                .userId(userId)
                .symbol(decision.symbol())
                .signalScore((int) Math.round(decision.score()))
                .grade(decision.signalScore().grade())
                .entryPrice(MoneyUtils.bd(decision.signalScore().entryPrice()))
                .stopLoss(MoneyUtils.bd(decision.signalScore().suggestedStopLoss()))
                .scanTime(LocalDateTime.now())
                .approvalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                .analysisReason(decision.signalScore().reason())
                .build();

        signal = screeningRepo.save(signal);
        approveAndExecute(userId, signal.getId(), effectivePaperMode, currentVix);
    }

    @Transactional
    public void approveAndExecute(Long userId, Long signalId, boolean isPaper, double currentVix) {
        StockScreeningResult signal = screeningRepo.findByIdAndUserId(signalId, userId)
                .orElseThrow(() -> new RuntimeException("Signal not found"));

        if (signal.getApprovalStatus() == StockScreeningResult.ApprovalStatus.EXECUTED) return;

        List<com.apex.backend.model.Candle> candles = fyersService.getHistoricalData(signal.getSymbol(), 200, "5");
        DecisionResult pipelineDecision = tradeDecisionPipelineService.evaluate(new PipelineRequest(
                userId,
                signal.getSymbol(),
                "5",
                candles,
                null
        ));
        if (pipelineDecision.action() != DecisionResult.DecisionAction.BUY) {
            metricsService.recordReject("PIPELINE_HOLD");
            signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.REJECTED);
            screeningRepo.save(signal);
            return;
        }
        GuardBlock guardBlock = evaluateGuards(userId, signal.getSymbol(), candles, pipelineDecision);
        if (guardBlock.blocked()) {
            metricsService.recordReject(guardBlock.reasonCode());
            decisionAuditService.record(signal.getSymbol(), "5m", guardBlock.auditType(), Map.of(
                    "reason", guardBlock.reason(),
                    "code", guardBlock.reasonCode()
            ));
            signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.REJECTED);
            screeningRepo.save(signal);
            broadcastService.broadcastBotStatus(Map.of(
                    "status", "BLOCKED",
                    "reason", guardBlock.reason(),
                    "timestamp", LocalDateTime.now()
            ));
            broadcastService.broadcastReject(new BroadcastService.RejectEvent(
                    guardBlock.reasonCode(),
                    null,
                    null,
                    signal.getSymbol(),
                    signal.getId(),
                    null
            ));
            return;
        }
        BigDecimal equity = MoneyUtils.bd(portfolioService.getAvailableEquity(isPaper, userId));
        BigDecimal stopLoss = MoneyUtils.bd(pipelineDecision.signalScore().suggestedStopLoss());
        BigDecimal entryPrice = MoneyUtils.bd(pipelineDecision.signalScore().entryPrice());
        double stopMultiplier = strategyProperties.getAtr().getStopMultiplier();
        BigDecimal atr = stopMultiplier == 0
                ? MoneyUtils.ZERO
                : MoneyUtils.scale(entryPrice.subtract(stopLoss).abs().divide(BigDecimal.valueOf(stopMultiplier), MoneyUtils.SCALE, java.math.RoundingMode.HALF_UP));

        int qty = pipelineDecision.riskDecision().recommendedQuantity();
        if (qty == 0) {
            qty = hybridPositionSizingService.calculateSizing(equity, entryPrice, stopLoss, atr, userId, pipelineDecision.score()).quantity();
        }
        qty = (int) Math.floor(qty * pipelineDecision.riskDecision().sizingMultiplier());
        double dynamicMultiplier = hybridPositionSizingService.resolveDynamicMultiplier(pipelineDecision.score());
        if (strategyProperties.getSizing().getDynamic().isEnabled()) {
            decisionAuditService.record(signal.getSymbol(), "5m", "DYNAMIC_SIZING", Map.of(
                    "score", pipelineDecision.score(),
                    "multiplier", dynamicMultiplier
            ));
        }

        if (qty == 0) {
            metricsService.recordReject("SIZE_ZERO");
            signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.REJECTED);
            screeningRepo.save(signal);
            return;
        }

        if (!pipelineDecision.riskDecision().allowed()) {
            metricsService.recordReject("RISK_GATE");
            signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.REJECTED);
            screeningRepo.save(signal);
            return;
        }

        String clientOrderId = "SIG-" + signal.getId() + "-" + java.util.UUID.randomUUID();
        ExecutionEngine.ExecutionResult executionResult = executionEngine.execute(new ExecutionEngine.ExecutionRequestPayload(
                userId,
                signal.getSymbol(),
                qty,
                ExecutionCostModel.OrderType.MARKET,
                ExecutionCostModel.ExecutionSide.BUY,
                null,
                isPaper,
                clientOrderId,
                atr.doubleValue(),
                candles,
                entryPrice.doubleValue(),
                stopLoss.doubleValue(),
                false
        ));
        if (executionResult.status() != ExecutionEngine.ExecutionStatus.FILLED) {
            dlqService.logFailure("EXECUTE_TRADE", signal.getSymbol(), executionResult.status().name());
            signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.REJECTED);
            screeningRepo.save(signal);
            return;
        }
        if (executionResult.averagePrice() != null && executionResult.averagePrice().compareTo(BigDecimal.ZERO) > 0) {
            entryPrice = executionResult.averagePrice();
        }

        riskEngine.addOpenPosition(signal.getSymbol(), entryPrice.doubleValue());

        Trade trade = Trade.builder()
                .symbol(signal.getSymbol())
                .userId(userId)
                .quantity(qty)
                .tradeType(Trade.TradeType.LONG)
                .entryPrice(entryPrice)
                .entryTime(LocalDateTime.now())
                .stopLoss(stopLoss)
                .currentStopLoss(stopLoss)
                .atr(atr)
                .highestPrice(entryPrice)
                .isPaperTrade(isPaper)
                .status(Trade.TradeStatus.OPEN)
                .positionState(com.apex.backend.model.PositionState.OPENING)
                .initialRiskAmount(entryPrice.subtract(stopLoss).abs())
                .sizingMultiplier(MoneyUtils.bd(dynamicMultiplier))
                .build();

        tradeRepo.save(trade);
        
        // PLACE PROTECTIVE STOP IMMEDIATELY
        if (!isPaper) {
            String token = fyersAuthService.getFyersToken(userId);
            if (token != null && !token.isBlank()) {
                boolean stopPlaced = stopLossPlacementService.placeProtectiveStop(trade, token).join();
                
                if (!stopPlaced) {
                    // STOP PLACEMENT FAILED - ENTER ERROR STATE
                    log.error("Stop-loss placement failed for trade: {} symbol: {}", trade.getId(), trade.getSymbol());
                    trade.transitionTo(com.apex.backend.model.PositionState.ERROR);
                    tradeRepo.save(trade);
                    
                    // Attempt immediate flatten
                    flattenPosition(trade, token);
                    
                    // Halt new trading
                    circuitBreakerService.triggerGlobalHalt("STOP_LOSS_PLACEMENT_FAILED: " + trade.getSymbol());
                    
                    // Emit alert
                    alertService.sendAlert("STOP_FAILED", "Failed to place stop for " + trade.getSymbol());
                    
                    signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.REJECTED);
                    screeningRepo.save(signal);
                    return;
                }
                
                // Stop placed successfully
                trade.transitionTo(com.apex.backend.model.PositionState.OPEN);
                tradeRepo.save(trade);
                log.info("Protective stop placed for trade: {} symbol: {}", trade.getId(), trade.getSymbol());
            } else {
                log.warn("No FYERS token available for stop placement, trade: {}", trade.getId());
                trade.transitionTo(com.apex.backend.model.PositionState.ERROR);
                tradeRepo.save(trade);
            }
        } else {
            // Paper mode: simulate stop placement
            trade.setStopOrderId("PAPER-STOP-" + System.currentTimeMillis());
            trade.setStopOrderState(com.apex.backend.model.OrderState.ACKED);
            trade.setStopAckedAt(LocalDateTime.now());
            trade.transitionTo(com.apex.backend.model.PositionState.OPEN);
            tradeRepo.save(trade);
        }
        
        decisionAuditService.record(signal.getSymbol(), "5m", "SIGNAL_SCORE", Map.of(
                "score", pipelineDecision.score(),
                "grade", pipelineDecision.signalScore().grade(),
                "reason", pipelineDecision.signalScore().reason()
        ));
        if (isPaper) {
            paperTradingService.recordEntry(userId, trade);
        }
        tradeFeatureAttributionService.saveAttributions(trade.getId(), userId, signal.getSymbol(), pipelineDecision.signalScore().featureContributions());
        signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.EXECUTED);
        screeningRepo.save(signal);
    }

    public void approveAndExecute(Long userId, Long signalId, boolean isPaper) {
        approveAndExecute(userId, signalId, isPaper, 15.0);
    }

    private GuardBlock evaluateGuards(Long userId, String symbol, List<com.apex.backend.model.Candle> candles, DecisionResult pipelineDecision) {
        if (systemGuardService.getState().isSafeMode()) {
            return new GuardBlock(true, "GUARD", "SAFE_MODE", "SAFE MODE: reconciliation mismatch");
        }
        TradingWindowService.WindowDecision windowDecision = tradingWindowService.evaluate(Instant.now());
        if (!windowDecision.allowed()) {
            return new GuardBlock(true, "TIME_FILTER", "TIME_WINDOW", windowDecision.reason());
        }
        if (userId != null) {
            var guardDecision = tradingGuardService.canTrade(userId, Instant.now());
            if (!guardDecision.allowed()) {
                return new GuardBlock(true, "GUARD", "CIRCUIT_BREAKER", guardDecision.reason());
            }
        }
        if (strategyProperties.getMarketGate().isEnabled()) {
            MarketGateService.MarketGateDecision gate = marketGateService.evaluateForLong(Instant.now());
            if (!gate.allowed()) {
                return new GuardBlock(true, "MARKET_GATE", "MARKET_GATE", gate.reason());
            }
        }
        if (strategyProperties.getVolShock().isEnabled()) {
            var shock = volShockService.evaluate(symbol, candles, strategyProperties.getVolShock().getLookback(),
                    strategyProperties.getVolShock().getMultiplier(), Instant.now());
            if (shock.shocked()) {
                return new GuardBlock(true, "VOL_SHOCK", "VOL_SHOCK", shock.reason());
            }
        }
        if (advancedTradingProperties.getLiquidity().isGateEnabled()) {
            double lastPrice = pipelineDecision.signalScore() != null ? pipelineDecision.signalScore().entryPrice() : 0.0;
            var liquidityDecision = liquidityGateService.evaluate(symbol, candles, lastPrice);
            if (!liquidityDecision.allowed()) {
                return new GuardBlock(true, "LIQUIDITY_GATE", "LIQUIDITY", liquidityDecision.reason());
            }
        }
        return new GuardBlock(false, null, null, null);
    }

    private record GuardBlock(boolean blocked, String auditType, String reasonCode, String reason) {}
    
    /**
     * Flatten position immediately (emergency exit)
     */
    private void flattenPosition(Trade trade, String token) {
        try {
            log.warn("Attempting to flatten position: {} symbol: {}", trade.getId(), trade.getSymbol());
            ExecutionEngine.ExecutionRequestPayload exitRequest = new ExecutionEngine.ExecutionRequestPayload(
                trade.getUserId(),
                trade.getSymbol(),
                trade.getQuantity(),
                com.apex.backend.service.ExecutionCostModel.OrderType.MARKET,
                trade.getTradeType() == Trade.TradeType.LONG 
                    ? com.apex.backend.service.ExecutionCostModel.ExecutionSide.SELL 
                    : com.apex.backend.service.ExecutionCostModel.ExecutionSide.BUY,
                null,
                false,
                "FLATTEN-" + trade.getId(),
                trade.getAtr() != null ? trade.getAtr().doubleValue() : 0.0,
                null,
                trade.getEntryPrice().doubleValue(),
                null,
                true,  // exitOrder = true
                null   // signalId
            );
            executionEngine.execute(exitRequest);
            log.info("Flatten order executed for trade: {}", trade.getId());
        } catch (Exception e) {
            log.error("Failed to flatten position: {} symbol: {}", trade.getId(), trade.getSymbol(), e);
            alertService.sendAlert("FLATTEN_FAILED", "Failed to flatten " + trade.getSymbol() + ": " + e.getMessage());
        }
    }
}
