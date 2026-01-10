package com.apex.backend.service.indicator;

import com.apex.backend.config.AdvancedTradingProperties;
import com.apex.backend.dto.MacdConfirmationDto;
import com.apex.backend.model.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MacdConfirmationService {

    private final MacdService macdService;
    private final AdvancedTradingProperties advancedTradingProperties;

    public MacdConfirmationDto confirm(List<Candle> candles) {
        if (candles == null || candles.size() < 3) {
            return new MacdConfirmationDto(0, 0, 0, false, false, false, false, false, false, false, false);
        }
        MacdService.MacdSeries series = macdService.calculateSeries(candles);
        List<Double> macdLine = series.macdLine();
        List<Double> signalLine = series.signalLine();
        List<Double> histogram = series.histogram();
        int lastIndex = macdLine.size() - 1;
        int prevIndex = lastIndex - 1;

        Double macd = macdLine.get(lastIndex);
        Double signal = signalLine.get(lastIndex);
        Double prevMacd = macdLine.get(prevIndex);
        Double prevSignal = signalLine.get(prevIndex);
        Double hist = histogram.get(lastIndex);
        Double prevHist = histogram.get(prevIndex);

        if (macd == null || signal == null || prevMacd == null || prevSignal == null || hist == null || prevHist == null) {
            return new MacdConfirmationDto(0, 0, 0, false, false, false, false, false, false, false, false);
        }

        boolean bullishCrossover = prevMacd <= prevSignal && macd > signal;
        boolean bearishCrossover = prevMacd >= prevSignal && macd < signal;
        boolean zeroLineCrossUp = prevMacd <= 0 && macd > 0;
        boolean zeroLineCrossDown = prevMacd >= 0 && macd < 0;

        int slopeLookback = advancedTradingProperties.getMacdConfirmation().getHistogramSlopeLookback();
        boolean histogramIncreasing = isHistogramTrending(histogram, slopeLookback, true);
        boolean histogramDecreasing = isHistogramTrending(histogram, slopeLookback, false);

        Divergence divergence = detectDivergence(candles, macdLine);

        return new MacdConfirmationDto(
                macd,
                signal,
                hist,
                bullishCrossover,
                bearishCrossover,
                zeroLineCrossUp,
                zeroLineCrossDown,
                histogramIncreasing,
                histogramDecreasing,
                divergence.bullish,
                divergence.bearish
        );
    }

    private boolean isHistogramTrending(List<Double> histogram, int lookback, boolean increasing) {
        if (histogram == null || histogram.size() < lookback + 1) {
            return false;
        }
        int end = histogram.size() - 1;
        Double startVal = histogram.get(end - lookback);
        Double endVal = histogram.get(end);
        if (startVal == null || endVal == null) {
            return false;
        }
        return increasing ? endVal > startVal : endVal < startVal;
    }

    private Divergence detectDivergence(List<Candle> candles, List<Double> macdLine) {
        int lookback = advancedTradingProperties.getMacdConfirmation().getDivergenceLookback();
        int startIndex = Math.max(1, candles.size() - lookback);
        List<Integer> priceLows = new ArrayList<>();
        List<Integer> priceHighs = new ArrayList<>();
        for (int i = startIndex; i < candles.size() - 1; i++) {
            double prev = candles.get(i - 1).getClose();
            double curr = candles.get(i).getClose();
            double next = candles.get(i + 1).getClose();
            if (curr < prev && curr < next) {
                priceLows.add(i);
            }
            if (curr > prev && curr > next) {
                priceHighs.add(i);
            }
        }
        boolean bullish = false;
        boolean bearish = false;
        if (priceLows.size() >= 2) {
            int last = priceLows.get(priceLows.size() - 1);
            int prev = priceLows.get(priceLows.size() - 2);
            Double macdLast = macdLine.get(last);
            Double macdPrev = macdLine.get(prev);
            if (macdLast != null && macdPrev != null) {
                double priceLast = candles.get(last).getClose();
                double pricePrev = candles.get(prev).getClose();
                bullish = priceLast < pricePrev && macdLast > macdPrev;
            }
        }
        if (priceHighs.size() >= 2) {
            int last = priceHighs.get(priceHighs.size() - 1);
            int prev = priceHighs.get(priceHighs.size() - 2);
            Double macdLast = macdLine.get(last);
            Double macdPrev = macdLine.get(prev);
            if (macdLast != null && macdPrev != null) {
                double priceLast = candles.get(last).getClose();
                double pricePrev = candles.get(prev).getClose();
                bearish = priceLast > pricePrev && macdLast < macdPrev;
            }
        }
        return new Divergence(bullish, bearish);
    }

    private record Divergence(boolean bullish, boolean bearish) {}
}
