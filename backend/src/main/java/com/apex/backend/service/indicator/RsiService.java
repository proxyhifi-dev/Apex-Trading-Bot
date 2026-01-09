package com.apex.backend.service.indicator;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RsiService {

    private final StrategyProperties strategyProperties;

    public RsiResult calculate(List<Candle> candles) {
        if (candles == null || candles.size() < 2) {
            return new RsiResult(50.0);
        }
        int period = strategyProperties.getRsi().getPeriod();
        if (candles.size() < period + 1) {
            return new RsiResult(50.0);
        }

        double avgGain = 0.0;
        double avgLoss = 0.0;
        for (int i = 1; i <= period; i++) {
            double change = candles.get(i).getClose() - candles.get(i - 1).getClose();
            if (change > 0) {
                avgGain += change;
            } else {
                avgLoss += Math.abs(change);
            }
        }
        avgGain /= period;
        avgLoss /= period;

        for (int i = period + 1; i < candles.size(); i++) {
            double change = candles.get(i).getClose() - candles.get(i - 1).getClose();
            double gain = Math.max(change, 0.0);
            double loss = Math.max(-change, 0.0);
            avgGain = ((avgGain * (period - 1)) + gain) / period;
            avgLoss = ((avgLoss * (period - 1)) + loss) / period;
        }

        if (avgLoss == 0) {
            return new RsiResult(100.0);
        }
        double rs = avgGain / avgLoss;
        double rsi = 100.0 - (100.0 / (1.0 + rs));
        return new RsiResult(rsi);
    }

    public record RsiResult(double rsi) {}
}
