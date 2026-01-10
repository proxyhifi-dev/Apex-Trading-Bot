package com.apex.backend.trading.pipeline;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Candle;
import com.apex.backend.service.SmartSignalGenerator;
import com.apex.backend.service.StrategyScoringService;
import com.apex.backend.service.StrategyScoringService.ScoreBreakdown;
import com.apex.backend.service.FeatureAttributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultSignalEngine implements SignalEngine {

    private final MarketDataProvider marketDataProvider;
    private final SmartSignalGenerator smartSignalGenerator;
    private final StrategyScoringService strategyScoringService;
    private final FeatureAttributionService featureAttributionService;
    private final StrategyProperties strategyProperties;

    @Override
    public SignalScore score(PipelineRequest request) {
        List<Candle> primary = request.candles();
        if (primary == null || primary.size() < 50) {
            return new SignalScore(false, 0.0, "N/A", 0.0, 0.0, "Insufficient data", null, List.of());
        }
        List<Candle> m15 = marketDataProvider.getCandles(request.symbol(), "15", 200);
        List<Candle> h1 = marketDataProvider.getCandles(request.symbol(), "60", 200);
        List<Candle> daily = marketDataProvider.getCandles(request.symbol(), "D", 200);

        SmartSignalGenerator.SignalDecision decision = smartSignalGenerator.generateSignalSmart(
                request.symbol(),
                primary,
                m15,
                h1,
                daily
        );
        ScoreBreakdown breakdown = strategyScoringService.score(primary);
        FeatureVector featureVector = featureAttributionService.buildFeatureVector(breakdown);
        List<FeatureContribution> contributions = featureAttributionService.computeContributions(featureVector, strategyProperties.getScoring());

        return new SignalScore(
                decision.isHasSignal(),
                decision.getScore(),
                decision.getGrade(),
                decision.getEntryPrice(),
                decision.getSuggestedStopLoss(),
                decision.getReason(),
                featureVector,
                contributions
        );
    }
}
