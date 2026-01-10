package com.apex.backend.service.indicator;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.dto.CandleConfirmationResult;
import com.apex.backend.model.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CandleConfirmationValidator {

    private final AdvancedTradingProperties advancedTradingProperties;

    public CandleConfirmationResult confirm(List<Candle> candles) {
        if (candles == null || candles.size() < 3) {
            return new CandleConfirmationResult(false, false, false);
        }
        int required = advancedTradingProperties.getCandleConfirmation().getRequiredCandles();
        if (candles.size() < required + 1) {
            return new CandleConfirmationResult(false, false, false);
        }
        boolean bullish = true;
        boolean bearish = true;
        int start = candles.size() - required;
        for (int i = start; i < candles.size(); i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);
            bullish = bullish && curr.getClose() > prev.getClose() && curr.getLow() >= prev.getLow();
            bearish = bearish && curr.getClose() < prev.getClose() && curr.getHigh() <= prev.getHigh();
        }
        boolean volumeConfirmed = confirmVolume(candles);
        return new CandleConfirmationResult(bullish, bearish, volumeConfirmed);
    }

    private boolean confirmVolume(List<Candle> candles) {
        if (!advancedTradingProperties.getCandleConfirmation().isVolumeConfirmation()) {
            return true;
        }
        int avgPeriod = advancedTradingProperties.getCandleConfirmation().getAvgVolumePeriod();
        if (candles.size() < avgPeriod + 1) {
            return false;
        }
        int start = candles.size() - avgPeriod - 1;
        double avgVolume = candles.subList(start, candles.size() - 1)
                .stream()
                .mapToLong(Candle::getVolume)
                .average()
                .orElse(0.0);
        double multiplier = advancedTradingProperties.getCandleConfirmation().getVolumeMultiplier();
        Candle last = candles.get(candles.size() - 1);
        return last.getVolume() > avgVolume * multiplier;
    }
}
