package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.model.Candle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class RiskManagementEngine {

    private final StrategyConfig config;
    private final SectorService sectorService;
    private final FyersService fyersService;
    private final IndicatorEngine indicatorEngine;
    private final CircuitBreaker circuitBreaker;

    private int consecutiveLosses = 0;
    private double dailyLossToday = 0;
    private LocalDate lastTradingDay = LocalDate.now();

    private final Map<String, Double> openPositions = new HashMap<>();

    public RiskManagementEngine(StrategyConfig config,
                                SectorService sectorService,
                                FyersService fyersService,
                                IndicatorEngine indicatorEngine,
                                CircuitBreaker circuitBreaker) {
        this.config = config;
        this.sectorService = sectorService;
        this.fyersService = fyersService;
        this.indicatorEngine = indicatorEngine;
        this.circuitBreaker = circuitBreaker;
    }

    // ✅ FIXED: This is the method RiskController was looking for
    public boolean isTradingHalted(double currentEquity) {
        double limit = currentEquity * -config.getRisk().getDailyLossLimitPct();
        return dailyLossToday < limit;
    }

    public boolean canExecuteTrade(double currentEquity, String symbol, double entryPrice, double stopLoss, int quantity) {
        // Gate 7: Circuit Breaker
        if (!circuitBreaker.canTrade()) {
            log.warn("❌ Gate 7 Fail: Circuit Breaker Active");
            return false;
        }

        // Gate 9: Market Hours
        if (!isMarketHours()) return false;

        // Gate 8: Capital
        if (currentEquity < config.getRisk().getMinEquity()) return false;

        // Gate 4: Duplicate
        if (openPositions.containsKey(symbol)) return false;

        // Gate 3: Max Positions
        if (openPositions.size() >= config.getRisk().getMaxOpenPositions()) return false;

        // Gate 5: Sector
        String newSector = sectorService.getSector(symbol);
        long sectorCount = openPositions.keySet().stream()
                .filter(s -> sectorService.getSector(s).equals(newSector)).count();
        if (sectorCount >= config.getRisk().getMaxSectorPositions()) return false;

        // Gate 6: Correlation
        return checkCorrelation(symbol);
    }

    private boolean checkCorrelation(String newSymbol) {
        double maxCorr = config.getRisk().getMaxCorrelation();
        List<Candle> newHistory = fyersService.getHistoricalData(newSymbol, 50, "5");
        if (newHistory.size() < 50) return true;

        for (String openSymbol : openPositions.keySet()) {
            List<Candle> openHistory = fyersService.getHistoricalData(openSymbol, 50, "5");
            double correlation = indicatorEngine.calculateCorrelation(newHistory, openHistory);
            if (correlation > maxCorr) return false;
        }
        return true;
    }

    public void updateDailyLoss(double tradeResult) {
        if (!lastTradingDay.equals(LocalDate.now())) {
            dailyLossToday = 0;
            consecutiveLosses = 0;
            lastTradingDay = LocalDate.now();
        }
        dailyLossToday += tradeResult;
        if (tradeResult < 0) consecutiveLosses++;
        else consecutiveLosses = 0;

        circuitBreaker.updateMetrics();
    }

    private boolean isMarketHours() {
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(9, 15)) && now.isBefore(LocalTime.of(15, 15));
    }

    public int getConsecutiveLosses() { return consecutiveLosses; }
    public double getDailyLossPercent(double currentEquity) { return (Math.abs(dailyLossToday) / currentEquity) * 100.0; }
    public void addOpenPosition(String symbol, double price) { openPositions.put(symbol, price); }
    public void removeOpenPosition(String symbol) { openPositions.remove(symbol); }
}