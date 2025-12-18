package com.apex.backend.service;

import com.apex.backend.model.Candle;
import com.apex.backend.service.IndicatorEngine.*;
import com.apex.backend.service.ExitEngine.TradeState;
import com.apex.backend.service.ExitEngine.ExitDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.LinkedList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class BotStrategy {

    private final FyersService fyersService;
    private final IndicatorEngine indicatorEngine;

    // NEW ENGINES
    private final CircuitBreaker circuitBreaker;
    private final PositionSizingEngine sizingEngine;
    private final SqueezeEngine squeezeEngine;
    private final ExitEngine exitEngine;

    // CONFIG & STATE
    private String targetSymbol = null;
    private boolean isPaperTrading = true;
    private boolean isBotEnabled = false;
    private double portfolioEquity = 100000.0;
    private double dailyStartingEquity = 100000.0;

    // ACTIVE TRADE STATE (Managed by ExitEngine now)
    private TradeState activeTrade = null;
    private boolean hasBought = false;

    private final LinkedList<String> liveLogs = new LinkedList<>();

    @Scheduled(fixedRate = 5000)
    public void runStrategy() {
        if (!isBotEnabled || targetSymbol == null) return;

        // 1. Circuit Breaker Check
        if (!circuitBreaker.canTrade(portfolioEquity, dailyStartingEquity)) {
            if (isBotEnabled) {
                stopBot();
                addLog("🛑 CIRCUIT BREAKER TRIPPED. Bot Stopped.");
            }
            return;
        }

        // 2. Data Fetch
        List<Candle> history = fyersService.getHistoricalData(targetSymbol, 200);
        if (history.size() < 50) return;

        double currentPrice = history.get(history.size() - 1).getClose();

        // 3. Indicator Calculations (Centralized)
        MacdResult macd = indicatorEngine.calculateMACD(history);
        double atr = indicatorEngine.calculateATR(history, 14);
        BollingerResult bb = indicatorEngine.calculateBollingerBands(history, 20, 2.0);
        KeltnerResult kc = indicatorEngine.calculateKeltnerChannels(history, 20, 1.5);
        AdxResult adx = indicatorEngine.calculateADX(history, 14);

        // 4. Decision Flow
        if (hasBought && activeTrade != null) {
            // --- EXIT LOGIC (Delegated to ExitEngine) ---
            boolean momentumWeakness = (macd.getMacdLine() < macd.getSignalLine()); // Bearish cross

            ExitDecision decision = exitEngine.manageTrade(activeTrade, currentPrice, atr, momentumWeakness);

            if (decision.isShouldExit()) {
                executeSell(decision.getReason(), currentPrice);
            }

        } else {
            // --- ENTRY LOGIC ---
            checkForEntry(history, currentPrice, bb, kc, macd, adx, atr);
        }
    }

    private void checkForEntry(List<Candle> history, double currentPrice,
                               BollingerResult bb, KeltnerResult kc,
                               MacdResult macd, AdxResult adx, double atr) {

        // 1. Squeeze Check
        boolean isSqueezing = squeezeEngine.isSqueeze(history, bb.getUpper(), bb.getLower(), kc.getUpper(), kc.getLower());

        // 2. Setup Quality Scoring
        int score = 0;
        if (macd.isBullish()) score += 30;
        if (adx.isStrongTrend()) score += 20;
        if (isSqueezing) score += 20;
        if (indicatorEngine.calculateRSI(history, 14) > 55) score += 15;

        // 3. Execution (Min Score 65)
        if (score >= 65) {
            double stopLoss = currentPrice - (2.0 * atr);

            // Delegate Sizing
            int qty = sizingEngine.calculateQuantity(portfolioEquity, currentPrice, stopLoss, score);

            if (qty > 0) {
                executeBuy(currentPrice, stopLoss, qty, score);
            }
        }
    }

    private void executeBuy(double price, double sl, int qty, int score) {
        fyersService.placeOrder(targetSymbol, qty, "BUY", "MARKET", 0.0, isPaperTrading);

        // Initialize Trade State
        this.activeTrade = new TradeState(price, sl, qty);
        this.hasBought = true;

        addLog(String.format("🚀 BUY %s @ %.2f | Score: %d | Qty: %d", targetSymbol, price, score, qty));
    }

    private void executeSell(String reason, double price) {
        fyersService.placeOrder(targetSymbol, activeTrade.getQty(), "SELL", "MARKET", 0.0, isPaperTrading);

        double pnl = (price - activeTrade.getEntryPrice()) * activeTrade.getQty();
        portfolioEquity += pnl;

        // Update Circuit Breaker
        circuitBreaker.recordTrade(pnl);

        addLog(String.format("🚪 SELL %s @ %.2f | P&L: %.2f | Reason: %s", targetSymbol, price, pnl, reason));

        this.hasBought = false;
        this.activeTrade = null;
    }

    // --- Control Methods ---
    public void startBot(String symbol, String strategy, boolean paperMode) {
        this.targetSymbol = symbol;
        this.isPaperTrading = paperMode;
        this.isBotEnabled = true;
        this.circuitBreaker.resetDailyStats();
        this.dailyStartingEquity = this.portfolioEquity; // Reset daily baseline
        addLog("🟢 BOT STARTED: " + symbol);
    }

    public void stopBot() {
        this.isBotEnabled = false;
        addLog("🔴 BOT STOPPED");
    }

    // Getters for Controller
    public boolean getStatus() { return isBotEnabled; }
    public String getCurrentSymbol() { return targetSymbol; }
    public String getBotState() { return hasBought ? "IN_TRADE" : "SCANNING"; }
    public List<String> getLiveLogs() { return liveLogs; }
    public java.util.Map<String, String> getPerformance() {
        return java.util.Map.of("equity", String.format("%.2f", portfolioEquity));
    }
    public java.util.List<java.util.Map<String, Object>> getPaperOrders() { return new java.util.ArrayList<>(); } // Stub
    private void addLog(String msg) { liveLogs.add(LocalTime.now().toString().substring(0,8) + " | " + msg); if(liveLogs.size()>100) liveLogs.removeFirst(); log.info(msg); }
}