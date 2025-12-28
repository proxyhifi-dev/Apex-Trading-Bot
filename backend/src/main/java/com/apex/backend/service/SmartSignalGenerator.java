package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.model.Candle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SmartSignalGenerator {

    private final IndicatorEngine indicatorEngine;
    private final StrategyConfig config;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignalDecision {
        public boolean hasSignal;
        public String symbol;
        public int score;
        public String grade;
        public double entryPrice;
        public double suggestedStopLoss;
        public String reason;
        public double macdLine;
        public double adx;
        public double rsi;

        public static SignalDecision noSignal(String reason) {
            return SignalDecision.builder()
                    .hasSignal(false)
                    .reason(reason)
                    .score(0)
                    .grade("F")
                    .build();
        }

        public static SignalDecision signalGenerated(String symbol, int score, String grade, double entry, double sl, double macd, double adx, double rsi) {
            return SignalDecision.builder()
                    .hasSignal(true)
                    .symbol(symbol)
                    .score(score)
                    .grade(grade)
                    .entryPrice(entry)
                    .suggestedStopLoss(sl)
                    .reason("Score: " + score)
                    .macdLine(macd)
                    .adx(adx)
                    .rsi(rsi)
                    .build();
        }
    }

    public SignalDecision generateSignalSmart(String symbol, List<Candle> candles, double currentAtr, double avgAtr) {
        if (candles.size() < 50) return SignalDecision.noSignal("Insufficient Data");

        IndicatorEngine.AdxResult adxRes = indicatorEngine.calculateADX(candles);
        double rsi = indicatorEngine.calculateRSI(candles);
        IndicatorEngine.MacdResult macdRes = indicatorEngine.calculateMACD(candles);
        boolean squeeze = indicatorEngine.hasBollingerSqueeze(candles);

        double minAdx = config.getStrategy().getAdx().getThreshold();
        double rsiMin = config.getStrategy().getRsi().getGoldilocksMin();
        double rsiMax = config.getStrategy().getRsi().getGoldilocksMax();

        if (adxRes.getAdx() < minAdx) return SignalDecision.noSignal("ADX too low: " + String.format("%.1f", adxRes.getAdx()));
        if (rsi < rsiMin || rsi > rsiMax) return SignalDecision.noSignal("RSI out of zone: " + String.format("%.1f", rsi));

        int score = 0;
        // ✅ FIXED: Accessed .getScoring() directly from config, not getStrategy().getScoring()
        var weights = config.getScoring();

        if (macdRes.isBullish()) score += weights.getMomentumWeight();
        if (adxRes.isStrongTrend()) score += weights.getTrendWeight();
        if (rsi >= rsiMin && rsi <= rsiMax) score += weights.getRsiWeight();
        if (squeeze) score += weights.getSqueezeWeight();

        double close = candles.get(candles.size() - 1).getClose();
        double atrPercent = (currentAtr / close) * 100;
        if (atrPercent >= config.getStrategy().getAtr().getMinPercent() &&
                atrPercent <= config.getStrategy().getAtr().getMaxPercent()) {
            score += weights.getVolatilityWeight();
        }

        int minScore = config.getScanner().getMinScore();
        if (score >= minScore) {
            double sl = close - (currentAtr * config.getStrategy().getAtr().getStopMultiplier());
            return SignalDecision.signalGenerated(symbol, score, getGrade(score), close, sl, macdRes.getMacdLine(), adxRes.getAdx(), rsi);
        }

        return SignalDecision.noSignal("Score too low: " + score);
    }

    private String getGrade(int score) {
        if (score >= 90) return "A+++";
        if (score >= 85) return "A++";
        if (score >= 80) return "A+";
        if (score >= 75) return "A";
        return "B";
    }
}