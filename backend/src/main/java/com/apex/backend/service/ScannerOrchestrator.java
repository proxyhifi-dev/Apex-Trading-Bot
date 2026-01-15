package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Candle;
import com.apex.backend.trading.pipeline.DecisionResult;
import com.apex.backend.trading.pipeline.PipelineRequest;
import com.apex.backend.trading.pipeline.TradeDecisionPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScannerOrchestrator {

    private final StrategyConfig config;
    private final StrategyProperties strategyProperties;
    private final StockScreeningService screeningService;
    private final FyersService fyersService;
    private final TradeExecutionService tradeExecutionService;
    private final BotStatusService botStatusService;
    private final com.apex.backend.service.indicator.MarketRegimeDetector marketRegimeDetector;
    private final TradeDecisionPipelineService tradeDecisionPipelineService;
    private final Executor tradingExecutor;

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
        ConcurrentLinkedQueue<DecisionResult> candidates = new ConcurrentLinkedQueue<>();

        // 2. ⚡ PARALLEL SCAN
        List<CompletableFuture<Void>> futures = universe.stream()
                .map(symbol -> CompletableFuture.runAsync(() -> {
                    DecisionResult decision = processSymbol(symbol, userId);
                    botStatusService.incrementScannedStocks();
                    if (decision != null && decision.action() == DecisionResult.DecisionAction.BUY) {
                        candidates.add(decision);
                    }
                }, tradingExecutor))
                .toList();

        // Wait for all threads
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 3. 📊 RANKING & EXECUTION
        processCandidates(new ArrayList<>(candidates), currentVix, userId);
    }

    private void processCandidates(List<DecisionResult> candidates, double currentVix, Long userId) {
        if (candidates.isEmpty()) {
            log.info("🚫 No valid setups found.");
            return;
        }

        // Sort by Score DESC (Hunter Logic)
        candidates.sort(Comparator.comparingDouble(DecisionResult::score).reversed());

        int maxCandidates = strategyProperties.getScanner().getMaxCandidates();
        // Limit to Top N
        List<DecisionResult> topPicks = candidates.stream()
                .limit(maxCandidates)
                .collect(Collectors.toList());

        boolean requireManualApproval = strategyProperties.getScanner().isRequireManualApproval();
        log.info("🏆 Processing Top {} Picks (manual approval: {}):", topPicks.size(), requireManualApproval);

        for (DecisionResult decision : topPicks) {
            if (requireManualApproval) {
                log.info("📝 Queuing for approval: {} [Score: {}]", decision.symbol(), decision.score());
                screeningService.saveSignal(userId, decision);
            } else {
                log.info("👉 Executing: {} [Score: {}]", decision.symbol(), decision.score());
                // ✅ FIXED: Passing 'currentVix' (double) instead of 'isMarketBullish' (boolean)
                tradeExecutionService.executeAutoTrade(userId, decision, true, currentVix);
            }
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
            var regime = marketRegimeDetector.detectAndStore("NSE:NIFTY50-INDEX", "1d", niftyData);
            return regime == com.apex.backend.model.MarketRegime.TRENDING || regime == com.apex.backend.model.MarketRegime.LOW_VOL;
        } catch (Exception e) {
            return true;
        }
    }

    private DecisionResult processSymbol(String symbol, Long userId) {
        try {
            // ✅ FETCH MULTI-TIMEFRAME DATA (Matches SmartSignalGenerator)
            List<Candle> m5 = fyersService.getHistoricalData(symbol, 200, "5");

            if (m5.size() < 50) return null;

            return tradeDecisionPipelineService.evaluate(new PipelineRequest(
                    userId,
                    symbol,
                    "5",
                    m5,
                    null
            ));

        } catch (Exception e) {
            log.error("Scan error {}: {}", symbol, e.getMessage());
        }
        return null;
    }
}
