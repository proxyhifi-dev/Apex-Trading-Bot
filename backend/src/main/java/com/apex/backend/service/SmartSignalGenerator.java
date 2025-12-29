package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.model.Candle;
import com.apex.backend.service.IndicatorEngine.AdxResult;
import com.apex.backend.service.IndicatorEngine.MacdResult;
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

        // Helper to check signal
        public boolean isHasSignal() { return hasSignal; }
    }

    public SignalDecision generateSignalSmart(String symbol, List<Candle> m5, List<Candle> m15, List<Candle> h1, List<Candle> daily) {
        if (m5.size() < 50) return SignalDecision.builder().hasSignal(false).reason("Insufficient Data").build();

        AdxResult adxRes = indicatorEngine.calculateADX(m5);
        double rsi = indicatorEngine.calculateRSI(m5);
        MacdResult macdRes = indicatorEngine.calculateMACD(m5);
        boolean squeeze = indicatorEngine.hasBollingerSqueeze(m5);
        double currentAtr = indicatorEngine.calculateATR(m5, 14);

        double minAdx = config.getStrategy().getAdxThreshold();

        if (adxRes.getAdx() < minAdx) {
            return SignalDecision.builder().hasSignal(false).reason("ADX Low").build();
        }

        int score = 0;
        StringBuilder reason = new StringBuilder();

        if (macdRes.getMacdLine() > macdRes.getSignalLine()) { score += 20; reason.append("MACD "); }
        if (adxRes.getAdx() >= minAdx) { score += 20; reason.append("Trend "); }

        if (rsi >= 40 && rsi <= 70) { score += 20; reason.append("RSI "); }
        if (squeeze) { score += 20; reason.append("Squeeze "); }

        double close = m5.get(m5.size() - 1).getClose();
        if (close > indicatorEngine.calculateEMA(m5, 50)) { score += 10; reason.append("EMA50 "); }

        if (score >= config.getStrategy().getMinEntryScore()) {
            double sl = close - (currentAtr * 2.0);
            String grade = score >= 90 ? "A+" : (score >= 80 ? "A" : "B");

            return SignalDecision.builder()
                    .hasSignal(true)
                    .symbol(symbol)
                    .score(score)
                    .grade(grade)
                    .entryPrice(close)
                    .suggestedStopLoss(sl)
                    .reason(reason.toString())
                    .macdLine(macdRes.getMacdLine())
                    .adx(adxRes.getAdx())
                    .rsi(rsi)
                    .build();
        }

        return SignalDecision.builder().hasSignal(false).score(score).reason("Low Score").build();
    }
}