package com.apex.backend.service.indicator;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.dto.MacdConfirmationDto;
import com.apex.backend.dto.MultiTimeframeMomentumResult;
import com.apex.backend.model.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MultiTimeframeMomentumService {

    private final MacdConfirmationService macdConfirmationService;
    private final AdvancedTradingProperties advancedTradingProperties;

    public MultiTimeframeMomentumResult score(List<Candle> m5, List<Candle> m15, List<Candle> h1, List<Candle> daily) {
        Map<String, Double> weights = advancedTradingProperties.getMultiTimeframeMomentum().getWeights();
        Map<String, Double> tfScores = new HashMap<>();

        double m5Score = momentumScore(macdConfirmationService.confirm(m5));
        double m15Score = momentumScore(macdConfirmationService.confirm(m15));
        double h1Score = momentumScore(macdConfirmationService.confirm(h1));
        double dScore = momentumScore(macdConfirmationService.confirm(daily));

        tfScores.put("5m", m5Score);
        tfScores.put("15m", m15Score);
        tfScores.put("1h", h1Score);
        tfScores.put("1d", dScore);

        double weighted = m5Score * weights.getOrDefault("5m", 0.4)
                + m15Score * weights.getOrDefault("15m", 0.3)
                + h1Score * weights.getOrDefault("1h", 0.2)
                + dScore * weights.getOrDefault("1d", 0.1);

        double penalty = calculatePenalty(m5Score, m15Score, h1Score, dScore);
        double finalScore = weighted - penalty;
        return new MultiTimeframeMomentumResult(finalScore, tfScores, penalty);
    }

    private double momentumScore(MacdConfirmationDto confirmation) {
        if (confirmation.bullishCrossover() || confirmation.zeroLineCrossUp()) {
            return 1.0;
        }
        if (confirmation.bearishCrossover() || confirmation.zeroLineCrossDown()) {
            return -1.0;
        }
        if (confirmation.histogramIncreasing()) {
            return 0.5;
        }
        if (confirmation.histogramDecreasing()) {
            return -0.5;
        }
        return 0.0;
    }

    private double calculatePenalty(double m5, double m15, double h1, double d1) {
        double penalty = 0.0;
        double conflictPenalty = advancedTradingProperties.getMultiTimeframeMomentum().getConflictPenalty();
        if (Math.signum(m5) != 0 && Math.signum(h1) != 0 && Math.signum(m5) != Math.signum(h1)) {
            penalty += conflictPenalty;
        }
        if (Math.signum(m15) != 0 && Math.signum(d1) != 0 && Math.signum(m15) != Math.signum(d1)) {
            penalty += conflictPenalty;
        }
        return penalty;
    }
}
