package com.apex.backend.service;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.model.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WalkForwardValidationService {

    private final AdvancedTradingProperties advancedTradingProperties;
    private final BacktestEngine backtestEngine;

    public Map<String, Object> validate(String symbol, String timeframe, List<Candle> candles) {
        Map<String, Object> result = new HashMap<>();
        int inSample = advancedTradingProperties.getBacktest().getInSampleBars();
        int outSample = advancedTradingProperties.getBacktest().getOutSampleBars();
        List<Double> outSampleExpectancy = new ArrayList<>();

        for (int start = 0; start + inSample + outSample <= candles.size(); start += outSample) {
            List<Candle> inSampleData = candles.subList(start, start + inSample);
            List<Candle> outSampleData = candles.subList(start + inSample, start + inSample + outSample);
            double inSampleExpectancy = backtestEngine.calculateExpectancy(inSampleData);
            double outSampleExp = backtestEngine.calculateExpectancy(outSampleData);
            outSampleExpectancy.add(outSampleExp);
            result.put("lastInSampleExpectancy", inSampleExpectancy);
        }
        result.put("outSampleExpectancy", outSampleExpectancy);
        result.put("performanceDecay", detectDecay(outSampleExpectancy));
        return result;
    }

    private boolean detectDecay(List<Double> expectancy) {
        if (expectancy.size() < 3) {
            return false;
        }
        int lookback = advancedTradingProperties.getBacktest().getDecayLookbackTrades();
        int start = Math.max(0, expectancy.size() - lookback);
        double first = expectancy.get(start);
        double last = expectancy.get(expectancy.size() - 1);
        return last < first * 0.7;
    }
}
