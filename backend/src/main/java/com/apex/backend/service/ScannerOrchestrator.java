package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.model.Candle;
import com.apex.backend.service.SmartSignalGenerator.SignalDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScannerOrchestrator {

    private final StrategyConfig config;
    private final StockScreeningService screeningService;
    private final FyersService fyersService;
    private final IndicatorEngine indicatorEngine;
    private final SmartSignalGenerator signalGenerator;
    private final TradeExecutionService tradeExecutionService;
    private final BotStatusService botStatusService;

    // Thread pool for parallel execution
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public void runScanner(Long userId) {
        if (!config.getScanner().isEnabled()) return;
        if (userId == null) {
            log.warn("⚠️ Skipping scan because user id is not configured.");
            return;
        }

        // 1. 🌍 MARKET REGIME & VIX CHECK
        boolean isMarketBullish = checkMarketRegime();
        double currentVix = fetchVix(); // ✅ Fetch VIX for Sizing

        log.info("🌍 Market: {} | VIX: {}", isMarketBullish ? "BULL" : "BEAR", currentVix);

        List<String> universe = screeningService.getUniverse();
        log.info("🔭 Parallel Scanning {} symbols...", universe.size());
        botStatusService.resetScanProgress();
        botStatusService.setTotalStocks(universe.size());

        // Thread-safe collection
        ConcurrentLinkedQueue<SignalDecision> candidates = new ConcurrentLinkedQueue<>();

        // 2. ⚡ PARALLEL SCAN
        List<CompletableFuture<Void>> futures = universe.stream()
                .map(symbol -> CompletableFuture.runAsync(() -> {
                    SignalDecision decision = processSymbol(symbol);
                    botStatusService.incrementScannedStocks();
                    if (decision != null && decision.isHasSignal()) {
                        candidates.add(decision);
                    }
                }, executor))
                .toList();

        // Wait for all threads
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 3. 📊 RANKING & EXECUTION
        processCandidates(new ArrayList<>(candidates), currentVix, userId);
    }

    private void processCandidates(List<SignalDecision> candidates, double currentVix, Long userId) {
        if (candidates.isEmpty()) {
            log.info("🚫 No valid setups found.");
            return;
        }

        // Sort by Score DESC (Hunter Logic)
        candidates.sort(Comparator.comparingInt(SignalDecision::getScore).reversed());

        // Limit to Top 5
        List<SignalDecision> topPicks = candidates.stream()
                .limit(5)
                .collect(Collectors.toList());

        log.info("🏆 Executing Top {} Picks:", topPicks.size());

        for (SignalDecision decision : topPicks) {
            log.info("👉 Executing: {} [Score: {}]", decision.getSymbol(), decision.getScore());
            // ✅ FIXED: Passing 'currentVix' (double) instead of 'isMarketBullish' (boolean)
            tradeExecutionService.executeAutoTrade(userId, decision, true, currentVix);
        }
    }

    private double fetchVix() {
        try {
            // Fetch 1 Daily Candle of India VIX
            List<Candle> vixData = fyersService.getHistoricalData("NSE:INDIAVIX-INDEX", 1, "D");
            if (!vixData.isEmpty()) {
                return vixData.get(vixData.size() - 1).getClose();
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to fetch VIX. Defaulting to 15.0");
        }
        return 15.0; // Safe default
    }

    private boolean checkMarketRegime() {
        try {
            List<Candle> niftyData = fyersService.getHistoricalData("NSE:NIFTY50-INDEX", 200, "D");
            if (niftyData.isEmpty()) return true;
            double current = niftyData.get(niftyData.size() - 1).getClose();
            double ema200 = indicatorEngine.calculateEMA(niftyData, 200);
            return current > ema200;
        } catch (Exception e) {
            return true;
        }
    }

    private SignalDecision processSymbol(String symbol) {
        try {
            // ✅ FETCH MULTI-TIMEFRAME DATA (Matches SmartSignalGenerator)
            List<Candle> m5 = fyersService.getHistoricalData(symbol, 200, "5");
            List<Candle> m15 = fyersService.getHistoricalData(symbol, 200, "15");
            List<Candle> h1 = fyersService.getHistoricalData(symbol, 200, "60");
            List<Candle> daily = fyersService.getHistoricalData(symbol, 200, "D");

            if (m5.size() < 50) return null;

            // ✅ PASS ALL DATA
            return signalGenerator.generateSignalSmart(symbol, m5, m15, h1, daily);

        } catch (Exception e) {
            log.error("Scan error {}: {}", symbol, e.getMessage());
        }
        return null;
    }
}
