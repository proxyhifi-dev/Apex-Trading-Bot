package com.apex.backend.service;

import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.model.Trade;
import com.apex.backend.model.TradingStrategy;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.repository.TradeRepository;
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
    private final RiskManagementEngine riskManagementEngine;

    private static final double DEFAULT_STOP_LOSS_PCT = 0.02;

    @Transactional
    public Trade approveAndExecute(Long signalId, boolean isPaper) {
        try {
            StockScreeningResult signal = screeningRepo.findById(signalId)
                    .orElseThrow(() -> new RuntimeException("Signal not found: " + signalId));

            // Prevent double execution
            if (signal.getApprovalStatus() == StockScreeningResult.ApprovalStatus.EXECUTED) {
                log.warn("⚠️ Signal {} already executed!", signalId);
                return null;
            }

            TradingStrategy strategy = signal.getStrategy();
            double entryPrice = signal.getCurrentPrice();
            double stopLoss = entryPrice * (1 - DEFAULT_STOP_LOSS_PCT);
            double currentAtr = 0.0;
            double averageAtr = 0.0;
            double liveEquity = strategy.getInitialCapital();

            // 1. Calculate Quantity
            int qty = sizingEngine.calculateQuantityIntelligent(
                    liveEquity, entryPrice, stopLoss, signal.getSignalScore(),
                    currentAtr, averageAtr, riskManagementEngine
            );

            if (qty <= 0) {
                throw new RuntimeException("Calculated quantity is 0 (Check Equity/Risk settings)");
            }

            // 2. Check Risk Engine
            if (!riskManagementEngine.canExecuteTrade(liveEquity, signal.getSymbol(), entryPrice, stopLoss, qty)) {
                throw new RuntimeException("Risk Engine Rejected Trade (Daily Limit or Margin)");
            }

            // 3. Place Order (REAL or PAPER)
            String exchangeOrderId = "PAPER-" + System.currentTimeMillis();
            if (!isPaper) {
                // This throws exception if Token is missing
                fyersService.placeOrder(signal.getSymbol(), qty, "BUY", "MARKET", 0.0, false);
                // Note: ideally placeOrder should return the ID, currently void in your simple version
            }

            // 4. Save Trade to DB
            Trade trade = Trade.builder()
                    .strategy(strategy)
                    .symbol(signal.getSymbol())
                    .tradeType(Trade.TradeType.LONG)
                    .quantity(qty)
                    .entryPrice(entryPrice)
                    .entryTime(LocalDateTime.now())
                    .stopLoss(stopLoss)
                    .currentStopLoss(stopLoss)
                    .highestPrice(entryPrice)
                    .barsInTrade(0)
                    .breakevenMoved(false)
                    .isPaperTrade(isPaper)
                    .status(Trade.TradeStatus.OPEN)
                    .build();

            signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.EXECUTED);
            screeningRepo.save(signal);

            log.info("✅ Trade Executed Successfully: {} | Mode: {}", signal.getSymbol(), isPaper ? "PAPER" : "LIVE");
            return tradeRepo.save(trade);

        } catch (Exception e) {
            log.error("❌ Trade Execution Failed: {}", e.getMessage());
            throw new RuntimeException(e.getMessage()); // Pass error to Controller
        }
    }
}