package com.apex.backend.service;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Candle;
import com.apex.backend.service.indicator.AdxService;
import com.apex.backend.service.indicator.AtrService;
import com.apex.backend.service.indicator.MacdService;
import com.apex.backend.service.indicator.RsiService;
import com.apex.backend.service.indicator.SqueezeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StrategyScoringService {

    private final StrategyProperties strategyProperties;
    private final MacdService macdService;
    private final AdxService adxService;
    private final RsiService rsiService;
    private final AtrService atrService;
    private final SqueezeService squeezeService;

    public ScoreBreakdown score(List<Candle> candles) {
        MacdService.MacdResult macd = macdService.calculate(candles);
        AdxService.AdxResult adx = adxService.calculate(candles);
        RsiService.RsiResult rsi = rsiService.calculate(candles);
        AtrService.AtrResult atr = atrService.calculate(candles);
        SqueezeService.SqueezeResult squeeze = squeezeService.detect(candles);

        StrategyProperties.Scoring weights = strategyProperties.getScoring();
        double momentumScore = (macd.momentumScore() / 11.0) * weights.getMomentumWeight();
        double trendScore = (adx.adx() >= strategyProperties.getAdx().getStrong() ? 1.0 : 0.0) * weights.getTrendWeight();
        boolean rsiGoldilocks = rsi.rsi() >= strategyProperties.getRsi().getGoldilocksMin()
                && rsi.rsi() <= strategyProperties.getRsi().getGoldilocksMax();
        double rsiScore = (rsiGoldilocks ? 1.0 : 0.0) * weights.getRsiWeight();
        boolean atrValid = atr.atrPercent() >= strategyProperties.getAtr().getMinPercent()
                && atr.atrPercent() <= strategyProperties.getAtr().getMaxPercent();
        double volatilityScore = (atrValid ? 1.0 : 0.0) * weights.getVolatilityWeight();
        double squeezeScore = (squeeze.squeeze() ? 1.0 : 0.0) * weights.getSqueezeWeight();

        double weightSum = weights.getMomentumWeight()
                + weights.getTrendWeight()
                + weights.getRsiWeight()
                + weights.getVolatilityWeight()
                + weights.getSqueezeWeight();
        double rawScore = momentumScore + trendScore + rsiScore + volatilityScore + squeezeScore;
        double normalizedScore = weightSum == 0 ? 0 : (rawScore / weightSum) * 100.0;

        return new ScoreBreakdown(
                normalizedScore,
                momentumScore,
                trendScore,
                rsiScore,
                volatilityScore,
                squeezeScore,
                macd.momentumScore(),
                adx.adx(),
                rsi.rsi(),
                atr.atr(),
                atr.atrPercent(),
                squeeze.squeeze(),
                squeeze.bars(),
                squeeze.ratio()
        );
    }

    public record ScoreBreakdown(
            double totalScore,
            double momentumScore,
            double trendScore,
            double rsiScore,
            double volatilityScore,
            double squeezeScore,
            double macdMomentumScore,
            double adxValue,
            double rsiValue,
            double atrValue,
            double atrPercent,
            boolean squeezeActive,
            int squeezeBars,
            double squeezeRatio
    ) {}
}
