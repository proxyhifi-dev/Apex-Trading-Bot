package com.apex.backend.service;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Candle;
import com.apex.backend.service.StrategyScoringService.ScoreBreakdown;
import com.apex.backend.service.indicator.AdxService;
import com.apex.backend.service.indicator.AtrService;
import com.apex.backend.service.indicator.BollingerBandService;
import com.apex.backend.service.indicator.MacdService;
import com.apex.backend.service.indicator.RsiService;
import com.apex.backend.service.indicator.SqueezeService;
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

    private final StrategyProperties strategyProperties;
    private final MacdService macdService;
    private final AdxService adxService;
    private final RsiService rsiService;
    private final AtrService atrService;
    private final BollingerBandService bollingerBandService;
    private final SqueezeService squeezeService;
    private final StrategyScoringService strategyScoringService;

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
        public double atr;
        public double atrPercent;
        public boolean squeeze;
        public double macdMomentumScore;

        // Helper to check signal
        public boolean isHasSignal() { return hasSignal; }
    }

    public SignalDecision generateSignalSmart(String symbol, List<Candle> m5, List<Candle> m15, List<Candle> h1, List<Candle> daily) {
        if (m5.size() < 50) return SignalDecision.builder().hasSignal(false).reason("Insufficient Data").build();

        MacdService.MacdResult macdRes = macdService.calculate(m5);
        AdxService.AdxResult adxRes = adxService.calculate(m5);
        RsiService.RsiResult rsiRes = rsiService.calculate(m5);
        AtrService.AtrResult atrRes = atrService.calculate(m5);
        SqueezeService.SqueezeResult squeezeRes = squeezeService.detect(m5);
        BollingerBandService.BollingerBands bollinger = bollingerBandService.calculate(m5);
        ScoreBreakdown breakdown = strategyScoringService.score(m5);

        double minAdx = strategyProperties.getAdx().getThreshold();
        double close = m5.get(m5.size() - 1).getClose();

        boolean rsiGoldilocks = rsiRes.rsi() >= strategyProperties.getRsi().getGoldilocksMin()
                && rsiRes.rsi() <= strategyProperties.getRsi().getGoldilocksMax();
        boolean atrValid = atrRes.atrPercent() >= strategyProperties.getAtr().getMinPercent()
                && atrRes.atrPercent() <= strategyProperties.getAtr().getMaxPercent();
        boolean strongMomentum = macdRes.histogram() > 0
                && macdRes.momentumScore() >= strategyProperties.getMacd().getMinMomentumScore();
        boolean squeezeBreakout = squeezeRes.squeeze() && close > bollinger.upper();

        if (breakdown.totalScore() < 70) {
            return SignalDecision.builder().hasSignal(false).score((int) Math.round(breakdown.totalScore())).reason("Score Below 70").build();
        }

        if (adxRes.adx() < minAdx || !rsiGoldilocks || !atrValid || !(squeezeBreakout || strongMomentum)) {
            return SignalDecision.builder()
                    .hasSignal(false)
                    .score((int) Math.round(breakdown.totalScore()))
                    .reason("Entry conditions not met")
                    .build();
        }

        double sl = close - (atrRes.atr() * strategyProperties.getAtr().getStopMultiplier());
        String grade = breakdown.totalScore() >= 90 ? "A+++"
                : (breakdown.totalScore() >= 85 ? "A++"
                : (breakdown.totalScore() >= 80 ? "A+" : "A"));

        String reason = String.format(
                "Score=%.1f (momentum=%.1f trend=%.1f rsi=%.1f vol=%.1f squeeze=%.1f) | " +
                        "MACD momentum=%.1f ADX=%.1f RSI=%.1f ATR%%=%.2f squeeze=%s(%d,%.2f)",
                breakdown.totalScore(),
                breakdown.momentumScore(),
                breakdown.trendScore(),
                breakdown.rsiScore(),
                breakdown.volatilityScore(),
                breakdown.squeezeScore(),
                breakdown.macdMomentumScore(),
                breakdown.adxValue(),
                breakdown.rsiValue(),
                breakdown.atrPercent(),
                breakdown.squeezeActive(),
                breakdown.squeezeBars(),
                breakdown.squeezeRatio()
        );

        return SignalDecision.builder()
                .hasSignal(true)
                .symbol(symbol)
                .score((int) Math.round(breakdown.totalScore()))
                .grade(grade)
                .entryPrice(close)
                .suggestedStopLoss(sl)
                .reason(reason)
                .macdLine(macdRes.macdLine())
                .adx(adxRes.adx())
                .rsi(rsiRes.rsi())
                .atr(atrRes.atr())
                .atrPercent(atrRes.atrPercent())
                .squeeze(squeezeRes.squeeze())
                .macdMomentumScore(macdRes.momentumScore())
                .build();
    }
}
