package com.apex.backend.service;

import com.apex.backend.exception.BadRequestException;
import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.model.Trade;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class TradeExecutionService {

    private final FyersService fyersService;
    private final TradeRepository tradeRepo;
    private final StockScreeningResultRepository screeningRepo;
    private final PositionSizingEngine sizingEngine;
    private final RiskManagementEngine riskEngine;
    private final PortfolioService portfolioService;
    private final DeadLetterQueueService dlqService;
    private final PaperTradingService paperTradingService;
    private final SettingsService settingsService;
    private final StrategyProperties strategyProperties;

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

        int qty = sizingEngine.calculateQuantityIntelligent(equity, entryPrice, stopLoss, signal.getGrade(), currentVix, riskEngine);

        if (qty == 0 || !riskEngine.canExecuteTrade(equity.doubleValue(), signal.getSymbol(), entryPrice.doubleValue(), stopLoss.doubleValue(), qty)) {
            signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.REJECTED);
            screeningRepo.save(signal);
            return;
        }

        if (!isPaper) {
            try {
                String entryOrderId = fyersService.placeOrder(signal.getSymbol(), qty, "BUY", "MARKET", 0);

                if (entryOrderId != null && !entryOrderId.startsWith("ERROR")) {
                    log.info("✅ Entry Filled: {}", entryOrderId);
                    try {
                        fyersService.placeOrder(signal.getSymbol(), qty, "SELL", "SL-M", stopLoss.doubleValue());
                    } catch (Exception slEx) {
                        String errorMsg = "SL FAILED for " + signal.getSymbol() + ": " + slEx.getMessage();
                        log.error(errorMsg);
                        dlqService.logFailure("PLACE_SL_ORDER", signal.getSymbol(), errorMsg);
                        fyersService.placeOrder(signal.getSymbol(), qty, "SELL", "MARKET", 0);
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
                .build();

        tradeRepo.save(trade);
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
