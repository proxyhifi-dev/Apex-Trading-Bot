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
import com.apex.backend.config.StrategyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final HybridPositionSizingService hybridPositionSizingService;
    private final MetricsService metricsService;
    private final DecisionAuditService decisionAuditService;
    private final TradeDecisionPipelineService tradeDecisionPipelineService;
    private final TradeFeatureAttributionService tradeFeatureAttributionService;
    private final ExecutionEngine executionEngine;

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
        BigDecimal equity = MoneyUtils.bd(portfolioService.getAvailableEquity(isPaper, userId));
        BigDecimal stopLoss = MoneyUtils.bd(pipelineDecision.signalScore().suggestedStopLoss());
        BigDecimal entryPrice = MoneyUtils.bd(pipelineDecision.signalScore().entryPrice());
        double stopMultiplier = strategyProperties.getAtr().getStopMultiplier();
        BigDecimal atr = stopMultiplier == 0
                ? MoneyUtils.ZERO
                : MoneyUtils.scale(entryPrice.subtract(stopLoss).abs().divide(BigDecimal.valueOf(stopMultiplier), MoneyUtils.SCALE, java.math.RoundingMode.HALF_UP));

        int qty = pipelineDecision.riskDecision().recommendedQuantity();
        if (qty == 0) {
            qty = hybridPositionSizingService.calculateQuantity(equity, entryPrice, stopLoss, atr, userId);
        }
        qty = (int) Math.floor(qty * pipelineDecision.riskDecision().sizingMultiplier());

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
                .initialRiskAmount(entryPrice.subtract(stopLoss).abs())
                .build();

        tradeRepo.save(trade);
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
}
