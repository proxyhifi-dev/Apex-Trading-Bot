package com.apex.backend.service;

import com.apex.backend.exception.BadRequestException;
import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.OrderIntentRepository;
import com.apex.backend.util.MoneyUtils;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.service.SmartSignalGenerator.SignalDecision;
import com.apex.backend.config.StrategyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private final PortfolioHeatService portfolioHeatService;
    private final LiquidityValidator liquidityValidator;
    private final ExecutionCostModel executionCostModel;
    private final OrderIntentRepository orderIntentRepository;
    private final MetricsService metricsService;
    private final DecisionAuditService decisionAuditService;

    @Transactional
    public void executeAutoTrade(Long userId, SignalDecision decision, boolean isPaper, double currentVix) {
        if (userId == null) {
            throw new BadRequestException("User ID is required for trade execution");
        }
        boolean effectivePaperMode = settingsService.isPaperModeForUser(userId);
        log.info("🤖 Auto-Trade Signal: {} | VIX: {} | Mode: {}", decision.getSymbol(), currentVix, effectivePaperMode ? "PAPER" : "LIVE");
        log.info("📊 Score Breakdown: {}", decision.getReason());

        StockScreeningResult signal = StockScreeningResult.builder()
                .userId(userId)
                .symbol(decision.getSymbol())
                .signalScore(decision.getScore())
                .grade(decision.getGrade())
                .entryPrice(MoneyUtils.bd(decision.getEntryPrice()))
                .stopLoss(MoneyUtils.bd(decision.getSuggestedStopLoss()))
                .scanTime(LocalDateTime.now())
                .approvalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                .analysisReason(decision.getReason())
                .macdValue(decision.getMacdLine())
                .rsiValue(decision.getRsi())
                .adxValue(decision.getAdx())
                .build();

        signal = screeningRepo.save(signal);
        approveAndExecute(userId, signal.getId(), effectivePaperMode, currentVix);
    }

    @Transactional
    public void approveAndExecute(Long userId, Long signalId, boolean isPaper, double currentVix) {
        StockScreeningResult signal = screeningRepo.findByIdAndUserId(signalId, userId)
                .orElseThrow(() -> new RuntimeException("Signal not found"));

        if (signal.getApprovalStatus() == StockScreeningResult.ApprovalStatus.EXECUTED) return;

        BigDecimal equity = MoneyUtils.bd(portfolioService.getAvailableEquity(isPaper, userId));
        BigDecimal stopLoss = signal.getStopLoss();
        BigDecimal entryPrice = signal.getEntryPrice();
        double stopMultiplier = strategyProperties.getAtr().getStopMultiplier();
        BigDecimal atr = stopMultiplier == 0
                ? MoneyUtils.ZERO
                : MoneyUtils.scale(entryPrice.subtract(stopLoss).abs().divide(BigDecimal.valueOf(stopMultiplier), MoneyUtils.SCALE, java.math.RoundingMode.HALF_UP));

        int qty = hybridPositionSizingService.calculateQuantity(equity, entryPrice, stopLoss, atr, userId);

        if (qty == 0) {
            metricsService.recordReject("SIZE_ZERO");
            signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.REJECTED);
            screeningRepo.save(signal);
            return;
        }

        LiquidityValidator.LiquidityDecision liquidityDecision = liquidityValidator.validate(
                signal.getSymbol(),
                fyersService.getHistoricalData(signal.getSymbol(), 30, "D"),
                qty
        );
        if (!liquidityDecision.allowed()) {
            metricsService.recordReject("LIQUIDITY");
            signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.REJECTED);
            screeningRepo.save(signal);
            return;
        }
        qty = liquidityDecision.adjustedQty();

        if (!portfolioHeatService.withinHeatLimit(userId, equity, entryPrice, stopLoss, qty)
                || !portfolioHeatService.passesCorrelationCheck(signal.getSymbol(), userId)
                || !riskEngine.canExecuteTrade(equity.doubleValue(), signal.getSymbol(), entryPrice.doubleValue(), stopLoss.doubleValue(), qty)) {
            metricsService.recordReject("RISK_GATE");
            signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.REJECTED);
            screeningRepo.save(signal);
            return;
        }

        String clientOrderId = "SIG-" + signal.getId() + "-" + java.util.UUID.randomUUID();
        if (orderIntentRepository.findByClientOrderId(clientOrderId).isPresent()) {
            return;
        }
        orderIntentRepository.save(com.apex.backend.model.OrderIntent.builder()
                .clientOrderId(clientOrderId)
                .userId(userId)
                .symbol(signal.getSymbol())
                .side("BUY")
                .quantity(qty)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build());
        var executionCost = executionCostModel.estimateCost(clientOrderId, signal.getSymbol(), qty, entryPrice.doubleValue(), atr.doubleValue());

        if (!isPaper) {
            try {
                String entryOrderId = fyersService.placeOrder(signal.getSymbol(), qty, "BUY", "MARKET", 0, clientOrderId);

                if (entryOrderId != null && !entryOrderId.startsWith("ERROR")) {
                    log.info("✅ Entry Filled: {}", entryOrderId);
                    orderIntentRepository.findByClientOrderId(clientOrderId).ifPresent(intent -> {
                        intent.setStatus("PLACED");
                        orderIntentRepository.save(intent);
                    });
                    try {
                        fyersService.placeOrder(signal.getSymbol(), qty, "SELL", "SL-M", stopLoss.doubleValue(), clientOrderId + "-SL");
                    } catch (Exception slEx) {
                        String errorMsg = "SL FAILED for " + signal.getSymbol() + ": " + slEx.getMessage();
                        log.error(errorMsg);
                        dlqService.logFailure("PLACE_SL_ORDER", signal.getSymbol(), errorMsg);
                        fyersService.placeOrder(signal.getSymbol(), qty, "SELL", "MARKET", 0, clientOrderId + "-EXIT");
                        throw new RuntimeException(errorMsg);
                    }
                } else {
                    throw new RuntimeException("Entry Order Failed");
                }
            } catch (Exception e) {
                dlqService.logFailure("EXECUTE_TRADE", signal.getSymbol(), e.getMessage());
                signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.REJECTED);
                screeningRepo.save(signal);
                return;
            }
        } else {
            orderIntentRepository.findByClientOrderId(clientOrderId).ifPresent(intent -> {
                intent.setStatus("PAPER_FILLED");
                orderIntentRepository.save(intent);
            });
            if (executionCost.getExpectedCost() != null) {
                executionCostModel.updateRealizedCost(clientOrderId, executionCost.getExpectedCost().doubleValue(), "PAPER");
            }
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
        metricsService.incrementOrdersPlaced();
        decisionAuditService.record(signal.getSymbol(), "5m", "SIGNAL_SCORE", Map.of(
                "score", signal.getSignalScore(),
                "grade", signal.getGrade(),
                "reason", signal.getAnalysisReason()
        ));
        if (isPaper) {
            paperTradingService.recordEntry(userId, trade);
        }
        signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.EXECUTED);
        screeningRepo.save(signal);
    }

    public void approveAndExecute(Long userId, Long signalId, boolean isPaper) {
        approveAndExecute(userId, signalId, isPaper, 15.0);
    }
}
