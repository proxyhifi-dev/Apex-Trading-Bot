package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.model.Candle;
import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.model.TradingStrategy;
import com.apex.backend.model.WatchlistStock;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.repository.TradingStrategyRepository;
import com.apex.backend.repository.WatchlistStockRepository;
import com.apex.backend.service.SmartSignalGenerator.SignalDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockScreeningService {

    private final FyersService fyersService;
    private final TradingStrategyRepository strategyRepo;
    private final WatchlistStockRepository watchlistRepo;
    private final StockScreeningResultRepository screeningRepo;
    private final IndicatorEngine indicatorEngine;
    private final StrategyConfig config;
    private final SmartSignalGenerator signalGenerator;

    @Scheduled(fixedDelayString = "${apex.scanner.interval}000") // Added prefix apex.
    public void runDynamicScreening() {
        if (!config.getScanner().isEnabled()) {
            return;
        }

        log.info("🔍 Starting Dynamic Stock Screening...");

        List<String> universe = getUniverse();
        List<TradingStrategy> activeStrategies = strategyRepo.findByActiveTrue();

        if (activeStrategies.isEmpty()) {
            log.warn("⚠️ No active strategies found in DB");
            return;
        }
        TradingStrategy strategy = activeStrategies.get(0);

        int candidatesFound = 0;
        int minScore = config.getScanner().getMinScore();
        int maxCandidates = config.getScanner().getMaxCandidates();

        for (String symbol : universe) {
            if (candidatesFound >= maxCandidates) {
                log.info("✅ Max candidates ({}) reached, stopping scan", maxCandidates);
                break;
            }

            try {
                Thread.sleep(200); // Rate limit

                SignalDecision decision = processSymbolSmart(symbol, strategy);

                if (decision != null && decision.hasSignal && decision.score >= minScore) {
                    saveSignal(symbol, strategy, decision);
                    candidatesFound++;
                    log.info("🔥 Signal #{} saved: {} | Score: {} | Grade: {}",
                            candidatesFound, symbol, decision.score, decision.grade);
                }

            } catch (Exception e) {
                log.error("❌ Screening failed for {}: {}", symbol, e.getMessage());
            }
        }

        log.info("✅ Scan complete. Saved {} / {} candidates.", candidatesFound, universe.size());
    }

    private List<String> getUniverse() {
        // ✅ FIXED: Correct access path for symbols in new StrategyConfig
        List<String> symbols = config.getTrading().getUniverse().getSymbols();

        if (symbols != null && !symbols.isEmpty()) {
            log.debug("📋 Using configured universe: {} symbols", symbols.size());
            return symbols;
        }

        log.warn("⚠️ No universe configured, using Watchlist fallback");
        return getWatchlistSymbols();
    }

    private List<String> getWatchlistSymbols() {
        List<WatchlistStock> watchlist = watchlistRepo.findByActiveTrue();
        List<String> symbols = new ArrayList<>();
        for (WatchlistStock stock : watchlist) {
            symbols.add(stock.getSymbol());
        }
        return symbols;
    }

    private SignalDecision processSymbolSmart(String symbol, TradingStrategy strategy) {
        List<Candle> history = fyersService.getHistoricalData(symbol, 200);
        if (history.size() < 50) {
            return null;
        }

        double currentAtr = indicatorEngine.calculateATR(history, 10);
        double avgAtr = indicatorEngine.calculateATR(
                history.size() > 100 ? history.subList(0, 100) : history, 10
        );

        return signalGenerator.generateSignalSmart(symbol, history, currentAtr, avgAtr);
    }

    private void saveSignal(String symbol, TradingStrategy strategy, SignalDecision decision) {
        if (screeningRepo.findBySymbolAndApprovalStatus(symbol, StockScreeningResult.ApprovalStatus.PENDING).isPresent()) {
            return;
        }

        StockScreeningResult result = StockScreeningResult.builder()
                .strategy(strategy)
                .symbol(symbol)
                .scanTime(LocalDateTime.now())
                .currentPrice(decision.entryPrice)
                .signalScore(decision.score)
                .hasEntrySignal(true)
                .approvalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                .build();

        screeningRepo.save(result);
    }
}