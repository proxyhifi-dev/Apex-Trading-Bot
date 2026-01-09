package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.model.Candle;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class IndicatorEngine {

    private final StrategyConfig config;

    public IndicatorEngine(StrategyConfig config) {
        this.config = config;
    }

    // --- Inner Classes ---
    @Data @Builder public static class AdxResult { private double adx; private double plusDI; private double minusDI; }
    @Data @Builder public static class MacdResult { private double macdLine; private double signalLine; private double histogram; }
    @Data @Builder public static class BollingerResult { private double upper; private double middle; private double lower; }
    @Data @Builder public static class KeltnerResult { private double upper; private double lower; }

    // --- Core Calculations ---

    public AdxResult calculateADX(List<Candle> candles) {
        int period = config.getStrategy().getAdxPeriod();
        if (candles.size() < (period + 1)) {
            return AdxResult.builder().adx(0).build();
        }

        List<Double> tr = new ArrayList<>();
        List<Double> dmPlus = new ArrayList<>();
        List<Double> dmMinus = new ArrayList<>();

        for (int i = 1; i < candles.size(); i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);
            double highDiff = curr.getHigh() - prev.getHigh();
            double lowDiff = prev.getLow() - curr.getLow();

            tr.add(Math.max(curr.getHigh() - curr.getLow(),
                    Math.max(Math.abs(curr.getHigh() - prev.getClose()), Math.abs(curr.getLow() - prev.getClose()))));
            dmPlus.add((highDiff > lowDiff && highDiff > 0) ? highDiff : 0.0);
            dmMinus.add((lowDiff > highDiff && lowDiff > 0) ? lowDiff : 0.0);
        }

        if (tr.size() < period) {
            return AdxResult.builder().adx(0).build();
        }

        double smoothTR = tr.subList(0, period).stream().mapToDouble(d -> d).sum();
        double smoothPlus = dmPlus.subList(0, period).stream().mapToDouble(d -> d).sum();
        double smoothMinus = dmMinus.subList(0, period).stream().mapToDouble(d -> d).sum();

        List<Double> dxValues = new ArrayList<>();
        double plusDI = 0.0;
        double minusDI = 0.0;

        for (int i = period - 1; i < tr.size(); i++) {
            if (i > period - 1) {
                smoothTR = smoothTR - (smoothTR / period) + tr.get(i);
                smoothPlus = smoothPlus - (smoothPlus / period) + dmPlus.get(i);
                smoothMinus = smoothMinus - (smoothMinus / period) + dmMinus.get(i);
            }

            if (smoothTR == 0) {
                continue;
            }
            plusDI = 100.0 * (smoothPlus / smoothTR);
            minusDI = 100.0 * (smoothMinus / smoothTR);
            double diSum = plusDI + minusDI;
            double dx = diSum == 0 ? 0.0 : (Math.abs(plusDI - minusDI) / diSum) * 100.0;
            dxValues.add(dx);
        }

        if (dxValues.size() < period) {
            return AdxResult.builder().adx(0).plusDI(plusDI).minusDI(minusDI).build();
        }

        double adx = dxValues.subList(0, period).stream().mapToDouble(d -> d).average().orElse(0.0);
        for (int i = period; i < dxValues.size(); i++) {
            adx = ((adx * (period - 1)) + dxValues.get(i)) / period;
        }

        return AdxResult.builder().adx(adx).plusDI(plusDI).minusDI(minusDI).build();
    }

    public double calculateRSI(List<Candle> candles) {
        int period = config.getStrategy().getRsiPeriod();
        if (candles.size() < period + 1) return 50.0;

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

        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    public MacdResult calculateMACD(List<Candle> candles) {
        int fast = config.getStrategy().getMacdFastPeriod();
        int slow = config.getStrategy().getMacdSlowPeriod();
        int signal = config.getStrategy().getMacdSignalPeriod();

        List<Double> closes = candles.stream().map(Candle::getClose).toList();
        List<Double> fastSeries = calculateEmaSeries(closes, fast);
        List<Double> slowSeries = calculateEmaSeries(closes, slow);

        List<Double> macdSeries = new ArrayList<>();
        for (int i = 0; i < closes.size(); i++) {
            Double fastVal = fastSeries.get(i);
            Double slowVal = slowSeries.get(i);
            if (fastVal != null && slowVal != null) {
                macdSeries.add(fastVal - slowVal);
            }
        }

        if (macdSeries.size() < signal) {
            return MacdResult.builder().macdLine(0).signalLine(0).histogram(0).build();
        }

        List<Double> signalSeries = calculateEmaSeries(macdSeries, signal);
        double macdLine = macdSeries.get(macdSeries.size() - 1);
        double signalLine = signalSeries.get(signalSeries.size() - 1);

        return MacdResult.builder().macdLine(macdLine).signalLine(signalLine).histogram(macdLine - signalLine).build();
    }

    public boolean hasBollingerSqueeze(List<Candle> candles) {
        int period = config.getStrategy().getBollingerPeriod();
        if (candles.size() < period + 1) {
            return false;
        }

        BollingerResult currentBands = calculateBollingerBands(candles, candles.size() - period, period);
        KeltnerResult keltner = calculateKeltnerChannels(candles, period, 1.5);
        return currentBands.getUpper() < keltner.getUpper() && currentBands.getLower() > keltner.getLower();
    }

    public double calculateATR(List<Candle> candles, int period) {
        if (candles.size() < period + 1) return 0.0;
        double sumTR = 0;
        for (int i = 1; i <= period; i++) {
            Candle curr = candles.get(candles.size() - i);
            Candle prev = candles.get(candles.size() - i - 1);
            sumTR += Math.max(curr.getHigh() - curr.getLow(), Math.max(Math.abs(curr.getHigh() - prev.getClose()), Math.abs(curr.getLow() - prev.getClose())));
        }
        return sumTR / period;
    }

    public double calculateAverageATR(List<Candle> candles, int period) {
        return calculateATR(candles, period * 2);
    }

    public double calculateEMA(List<Candle> candles, int period) {
        if (candles.size() < period) return 0.0;
        List<Double> closes = candles.stream().map(Candle::getClose).toList();
        List<Double> emaSeries = calculateEmaSeries(closes, period);
        return emaSeries.get(emaSeries.size() - 1);
    }

    public BollingerResult calculateBollingerBands(List<Candle> candles, int startIndex, int period) {
        List<Candle> window = candles.subList(startIndex, startIndex + period);
        double mean = window.stream().mapToDouble(Candle::getClose).average().orElse(0.0);
        double variance = window.stream()
                .mapToDouble(candle -> {
                    double diff = candle.getClose() - mean;
                    return diff * diff;
                })
                .average()
                .orElse(0.0);
        double standardDeviation = Math.sqrt(variance);
        double stdDevMultiplier = config.getStrategy().getBollingerStdDev();
        double upper = mean + (standardDeviation * stdDevMultiplier);
        double lower = mean - (standardDeviation * stdDevMultiplier);
        return BollingerResult.builder()
                .upper(upper)
                .middle(mean)
                .lower(lower)
                .build();
    }

    // Correlation Method
    public double calculateCorrelation(List<Candle> seriesA, List<Candle> seriesB) {
        if (seriesA.size() != seriesB.size() || seriesA.size() < 2) return 0.0;
        List<Double> returnsA = calculateLogReturns(seriesA.stream().map(Candle::getClose).toList());
        List<Double> returnsB = calculateLogReturns(seriesB.stream().map(Candle::getClose).toList());
        if (returnsA.size() != returnsB.size() || returnsA.isEmpty()) return 0.0;
        int n = returnsA.size();
        double sumX = 0.0, sumY = 0.0, sumXY = 0.0, sumX2 = 0.0, sumY2 = 0.0;
        for (int i = 0; i < n; i++) {
            double x = returnsA.get(i);
            double y = returnsB.get(i);
            sumX += x; sumY += y; sumXY += x * y; sumX2 += x * x; sumY2 += y * y;
        }
        double numerator = (n * sumXY) - (sumX * sumY);
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
        return denominator == 0 ? 0 : numerator / denominator;
    }

    private List<Double> calculateEmaSeries(List<Double> values, int period) {
        List<Double> emaSeries = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            emaSeries.add(null);
        }
        if (values.size() < period) {
            return emaSeries;
        }
        double sma = values.subList(0, period).stream().mapToDouble(d -> d).average().orElse(0.0);
        emaSeries.set(period - 1, sma);
        double k = 2.0 / (period + 1);
        double ema = sma;
        for (int i = period; i < values.size(); i++) {
            ema = (values.get(i) * k) + (ema * (1 - k));
            emaSeries.set(i, ema);
        }
        return emaSeries;
    }

    private KeltnerResult calculateKeltnerChannels(List<Candle> candles, int period, double atrMultiplier) {
        double middle = calculateEMA(candles, period);
        double atr = calculateAtrWilder(candles, period);
        double upper = middle + (atr * atrMultiplier);
        double lower = middle - (atr * atrMultiplier);
        return KeltnerResult.builder().upper(upper).lower(lower).build();
    }

    private double calculateAtrWilder(List<Candle> candles, int period) {
        if (candles.size() < period + 1) {
            return 0.0;
        }
        List<Double> tr = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);
            tr.add(Math.max(curr.getHigh() - curr.getLow(),
                    Math.max(Math.abs(curr.getHigh() - prev.getClose()), Math.abs(curr.getLow() - prev.getClose()))));
        }
        if (tr.size() < period) {
            return 0.0;
        }
        double atr = tr.subList(0, period).stream().mapToDouble(d -> d).average().orElse(0.0);
        for (int i = period; i < tr.size(); i++) {
            atr = ((atr * (period - 1)) + tr.get(i)) / period;
        }
        return atr;
    }

    private List<Double> calculateLogReturns(List<Double> prices) {
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < prices.size(); i++) {
            double prev = prices.get(i - 1);
            double curr = prices.get(i);
            if (prev <= 0 || curr <= 0) {
                continue;
            }
            returns.add(Math.log(curr / prev));
        }
        return returns;
    }
}
