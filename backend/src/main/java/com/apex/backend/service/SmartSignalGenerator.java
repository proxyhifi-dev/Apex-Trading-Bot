package com.apex.backend.service;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.config.StrategyConfig;
import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Candle;
import com.apex.backend.service.StrategyScoringService.ScoreBreakdown;
import com.apex.backend.service.indicator.AdxService;
import com.apex.backend.service.indicator.AtrService;
import com.apex.backend.service.indicator.BollingerBandService;
import com.apex.backend.service.indicator.CandleConfirmationValidator;
import com.apex.backend.service.indicator.CandlePatternDetector;
import com.apex.backend.service.indicator.ChoppinessIndexService;
import com.apex.backend.service.indicator.DonchianChannelService;
import com.apex.backend.service.indicator.MacdConfirmationService;
import com.apex.backend.service.indicator.MacdService;
import com.apex.backend.service.indicator.MultiTimeframeMomentumService;
import com.apex.backend.service.indicator.RsiService;
import com.apex.backend.service.indicator.SqueezeService;
import com.apex.backend.service.indicator.VolShockService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SmartSignalGenerator {

    private final StrategyConfig strategyConfig;
    private final StrategyProperties strategyProperties;
    private final AdvancedTradingProperties advancedTradingProperties;
    private final MacdService macdService;
    private final AdxService adxService;
    private final RsiService rsiService;
    private final AtrService atrService;
    private final BollingerBandService bollingerBandService;
    private final SqueezeService squeezeService;
    private final StrategyScoringService strategyScoringService;
    private final MacdConfirmationService macdConfirmationService;
    private final CandleConfirmationValidator candleConfirmationValidator;
    private final CandlePatternDetector candlePatternDetector;
    private final MultiTimeframeMomentumService multiTimeframeMomentumService;
    private final DecisionAuditService decisionAuditService;
    private final ChoppinessIndexService choppinessIndexService;
    private final DonchianChannelService donchianChannelService;
    private final TradingWindowService tradingWindowService;
    private final com.apex.backend.service.risk.CircuitBreakerService circuitBreakerService;
    private final SystemGuardService systemGuardService;
    private final MarketGateService marketGateService;
    private final LiquidityGateService liquidityGateService;
    private final VolShockService volShockService;

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
        public double multiTimeframeScore;
        public String patternType;
        public double patternStrength;

        // Helper to check signal
        public boolean isHasSignal() { return hasSignal; }
    }

    public SignalDecision generateSignalSmart(String symbol, List<Candle> m5, List<Candle> m15, List<Candle> h1, List<Candle> daily) {
        if (m5.size() < 50) return SignalDecision.builder().hasSignal(false).reason("Insufficient Data").build();

        Instant now = Instant.now();
        if (systemGuardService.getState().isSafeMode()) {
            decisionAuditService.record(symbol, "5m", "GUARD", Map.of("reason", "SAFE_MODE"));
            return SignalDecision.builder().hasSignal(false).reason("SAFE MODE: reconciliation mismatch").build();
        }

        TradingWindowService.WindowDecision windowDecision = tradingWindowService.evaluate(now);
        if (!windowDecision.allowed()) {
            decisionAuditService.record(symbol, "5m", "TIME_FILTER", Map.of("reason", windowDecision.reason()));
            return SignalDecision.builder().hasSignal(false).reason("Time filter: " + windowDecision.reason()).build();
        }

        MacdService.MacdResult macdRes = macdService.calculate(m5);
        var macdConfirm = macdConfirmationService.confirm(m5);
        var candleConfirm = candleConfirmationValidator.confirm(m5);
        var pattern = candlePatternDetector.detect(m5);
        var multiTfScore = multiTimeframeMomentumService.score(m5, m15, h1, daily);
        AdxService.AdxResult adxRes = adxService.calculate(m5);
        RsiService.RsiResult rsiRes = rsiService.calculate(m5);
        AtrService.AtrResult atrRes = atrService.calculate(m5);
        SqueezeService.SqueezeResult squeezeRes = squeezeService.detect(m5);
        BollingerBandService.BollingerBands bollinger = bollingerBandService.calculate(m5);
        ScoreBreakdown breakdown = strategyScoringService.score(m5);

        double minAdx = strategyProperties.getAdx().getThreshold();
        double close = m5.get(m5.size() - 1).getClose();
        decisionAuditService.record(symbol, "5m", "PATTERN", Map.of(
                "pattern", pattern.type(),
                "bullish", pattern.bullish(),
                "strength", pattern.strengthScore()
        ));
        decisionAuditService.record(symbol, "MULTI_TF", "MOMENTUM", Map.of(
                "score", multiTfScore.score(),
                "penalty", multiTfScore.penaltyApplied()
        ));

        Long ownerUserId = strategyConfig.getTrading().getOwnerUserId();
        if (ownerUserId != null) {
            var guardDecision = circuitBreakerService.canTrade(ownerUserId, now);
            if (!guardDecision.allowed()) {
                decisionAuditService.record(symbol, "5m", "GUARD", Map.of("reason", guardDecision.reason(), "until", guardDecision.until()));
                return SignalDecision.builder().hasSignal(false).reason("Guard: " + guardDecision.reason()).build();
            }
        }

        if (strategyProperties.getMarketGate().isEnabled()) {
            MarketGateService.MarketGateDecision gate = marketGateService.evaluateForLong(now);
            decisionAuditService.record(symbol, "5m", "MARKET_GATE", Map.of(
                    "allowed", gate.allowed(),
                    "reason", gate.reason(),
                    "emaFast", gate.emaFast(),
                    "emaSlow", gate.emaSlow(),
                    "lastClose", gate.lastClose()
            ));
            if (!gate.allowed()) {
                return SignalDecision.builder().hasSignal(false).reason("Market gate: " + gate.reason()).build();
            }
        }

        if (strategyProperties.getVolShock().isEnabled()) {
            var shock = volShockService.evaluate(symbol, m5, strategyProperties.getVolShock().getLookback(),
                    strategyProperties.getVolShock().getMultiplier(), now);
            decisionAuditService.record(symbol, "5m", "VOL_SHOCK", Map.of(
                    "shocked", shock.shocked(),
                    "atrPct", shock.atrPct(),
                    "medianAtrPct", shock.medianAtrPct(),
                    "cooldownBars", shock.cooldownBarsRemaining()
            ));
            if (shock.shocked()) {
                return SignalDecision.builder().hasSignal(false).reason("Volatility shock: " + shock.reason()).build();
            }
        }

        if (advancedTradingProperties.getLiquidity().isGateEnabled()) {
            var liquidityDecision = liquidityGateService.evaluate(symbol, m5, close);
            decisionAuditService.record(symbol, "5m", "LIQUIDITY_GATE", Map.of(
                    "allowed", liquidityDecision.allowed(),
                    "reason", liquidityDecision.reason(),
                    "rupeeVolume", liquidityDecision.rupeeVolume(),
                    "spreadPct", liquidityDecision.spreadPct(),
                    "avgVolume", liquidityDecision.avgVolume()
            ));
            if (!liquidityDecision.allowed()) {
                return SignalDecision.builder().hasSignal(false).reason("Liquidity gate: " + liquidityDecision.reason()).build();
            }
        }

        AdvancedTradingProperties.MarketRegime regimeConfig = advancedTradingProperties.getMarketRegime();
        if (regimeConfig.isChopFilterEnabled()) {
            var chop = choppinessIndexService.calculate(m5, regimeConfig.getChopPeriod());
            boolean choppy = chop.chop() >= regimeConfig.getChoppyThreshold() && adxRes.adx() < regimeConfig.getTrendingAdxThreshold();
            decisionAuditService.record(symbol, "5m", "CHOP_FILTER", Map.of(
                    "chop", chop.chop(),
                    "threshold", regimeConfig.getChoppyThreshold(),
                    "adx", adxRes.adx(),
                    "choppy", choppy
            ));
            if (choppy) {
                return SignalDecision.builder().hasSignal(false).reason(String.format("Choppy market (CHOP=%.2f)", chop.chop())).build();
            }
        }

        boolean rsiGoldilocks = rsiRes.rsi() >= strategyProperties.getRsi().getGoldilocksMin()
                && rsiRes.rsi() <= strategyProperties.getRsi().getGoldilocksMax();
        boolean atrValid = atrRes.atrPercent() >= strategyProperties.getAtr().getMinPercent()
                && atrRes.atrPercent() <= strategyProperties.getAtr().getMaxPercent();
        boolean strongMomentum = macdRes.histogram() > 0
                && macdRes.momentumScore() >= strategyProperties.getMacd().getMinMomentumScore()
                && macdConfirm.bullishCrossover();
        boolean squeezeBreakout = squeezeRes.squeeze() && close > bollinger.upper();
        boolean candleConfirmed = candleConfirm.bullishConfirmed() && candleConfirm.volumeConfirmed();
        boolean structureBreakoutOk = true;
        if (strategyProperties.getBreakout().isUseDonchian()) {
            DonchianChannelService.Donchian channel = donchianChannelService.calculate(m5, strategyProperties.getBreakout().getDonchianPeriod());
            decisionAuditService.record(symbol, "5m", "DONCHIAN", Map.of(
                    "period", channel.period(),
                    "upper", channel.upper(),
                    "lower", channel.lower(),
                    "close", close
            ));
            if (strategyProperties.getBreakout().isRequireStructureBreakout()) {
                structureBreakoutOk = (squeezeBreakout || strongMomentum) && close > channel.upper();
                if (!structureBreakoutOk) {
                    return SignalDecision.builder()
                            .hasSignal(false)
                            .score((int) Math.round(breakdown.totalScore()))
                            .reason("Structure breakout not confirmed")
                            .build();
                }
            }
        }

        if (breakdown.totalScore() < 70) {
            return SignalDecision.builder().hasSignal(false).score((int) Math.round(breakdown.totalScore())).reason("Score Below 70").build();
        }

        if (adxRes.adx() < minAdx || !rsiGoldilocks || !atrValid || !(squeezeBreakout || strongMomentum) || !candleConfirmed || !structureBreakoutOk) {
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
                        "MACD momentum=%.1f ADX=%.1f RSI=%.1f ATR%%=%.2f squeeze=%s(%d,%.2f) pattern=%s(%.2f) mtf=%.2f",
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
                breakdown.squeezeRatio(),
                pattern.type(),
                pattern.strengthScore(),
                multiTfScore.score()
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
                .multiTimeframeScore(multiTfScore.score())
                .patternType(pattern.type())
                .patternStrength(pattern.strengthScore())
                .build();
    }
}
