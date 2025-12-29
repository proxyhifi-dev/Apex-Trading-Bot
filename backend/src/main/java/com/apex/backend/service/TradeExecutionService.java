package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.service.SmartSignalGenerator.SignalDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final StrategyConfig config;
    private final DeadLetterQueueService dlqService; // ✅ NEW: DLQ Injection

    @Transactional
    public void executeAutoTrade(SignalDecision decision, boolean isPaper, double currentVix) {
        boolean effectivePaperMode = config.getTrading().isPaperMode();
        log.info("🤖 Auto-Trade Signal: {} | VIX: {} | Mode: {}", decision.getSymbol(), currentVix, effectivePaperMode ? "PAPER" : "LIVE");

        StockScreeningResult signal = StockScreeningResult.builder()
                .symbol(decision.getSymbol())
                .signalScore(decision.getScore())
                .grade(decision.getGrade())
                .entryPrice(decision.getEntryPrice())
                .stopLoss(decision.getSuggestedStopLoss())
                .scanTime(LocalDateTime.now())
                .approvalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                .analysisReason(decision.getReason())
                .build();

        signal = screeningRepo.save(signal);
        approveAndExecute(signal.getId(), effectivePaperMode, currentVix);
    }

    @Transactional
    public void approveAndExecute(Long signalId, boolean isPaper, double currentVix) {
        StockScreeningResult signal = screeningRepo.findById(signalId)
                .orElseThrow(() -> new RuntimeException("Signal not found"));

        if (signal.getApprovalStatus() == StockScreeningResult.ApprovalStatus.EXECUTED) return;

        double equity = portfolioService.getAvailableEquity(isPaper);
        double stopLoss = signal.getStopLoss();
        double entryPrice = signal.getEntryPrice();
        double atr = (entryPrice - stopLoss) / 2.0;

        int qty = sizingEngine.calculateQuantityIntelligent(equity, entryPrice, stopLoss, signal.getGrade(), currentVix, riskEngine);

        if (qty == 0 || !riskEngine.canExecuteTrade(equity, signal.getSymbol(), entryPrice, stopLoss, qty)) {
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
                        fyersService.placeOrder(signal.getSymbol(), qty, "SELL", "SL-M", stopLoss);
                    } catch (Exception slEx) {
                        // 🚨 Emergency Fallback + DLQ
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
                // 🚨 Execution Error + DLQ
                dlqService.logFailure("EXECUTE_TRADE", signal.getSymbol(), e.getMessage());
                signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.REJECTED);
                screeningRepo.save(signal);
                return;
            }
        }

        riskEngine.addOpenPosition(signal.getSymbol(), entryPrice);

        Trade trade = Trade.builder()
                .symbol(signal.getSymbol())
                .quantity(qty)
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
        signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.EXECUTED);
        screeningRepo.save(signal);
    }

    public void approveAndExecute(Long signalId, boolean isPaper) {
        approveAndExecute(signalId, isPaper, 15.0);
    }
}