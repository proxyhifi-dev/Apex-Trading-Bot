package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.model.Candle;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IndicatorEngine {

    private final StrategyConfig config;

    public IndicatorEngine(StrategyConfig config) {
        this.config = config;
    }

    // --- Result Classes ---

    @Data
    @Builder
    public static class AdxResult {
        private double adx;
        private double plusDI;
        private double minusDI;
        private boolean isStrongTrend;
    }

    @Data
    @Builder
    public static class BollingerResult {
        private double upper;
        private double middle;
        private double lower;
        private double bandwidth;
    }

    @Data
    @Builder
    public static class KeltnerResult {
        private double upper;
        private double middle;
        private double lower;
        private double bandwidth;
    }

    @Data
    @Builder
    public static class MacdResult {
        private double macdLine;
        private double signalLine;
        private double histogram;
        private boolean isBullish;
    }

    // --- Methods used by SmartSignalGenerator (Overloads) ---

    public double calculateEMA(List<Candle> candles, int period) {
        if (candles == null || candles.isEmpty()) return 0.0;
        return calculateEMAFromValues(candles.stream().map(Candle::getClose).collect(Collectors.toList()), period);
    }

    public double calculateADX(List<Candle> candles, int period) {
        // Simplified return for double-based calls
        AdxResult result = calculateADXInternal(candles, period);
        return result.getAdx();
    }

    public double calculateRSI(List<Candle> candles, int period) {
        return calculateRSIInternal(candles, period);
    }

    public boolean hasBollingerSqueeze(List<Candle> candles) {
        if (candles.size() < 20) return false;
        BollingerResult bb = calculateBollingerBands(candles);
        KeltnerResult kc = calculateKeltnerChannels(candles);
        // Squeeze is when BB is inside KC
        return bb.getUpper() < kc.getUpper() && bb.getLower() > kc.getLower();
    }

    // --- Standard Methods used by other Services ---

    public double calculateATR(List<Candle> candles, int period) {
        if (candles.size() < period + 1) return 0.0;

        List<Double> trValues = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            double high = candles.get(i).getHigh();
            double low = candles.get(i).getLow();
            double prevClose = candles.get(i - 1).getClose();
            double tr = Math.max(high - low,
                    Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
            trValues.add(tr);
        }
        return calculateWildersSmoothing(trValues, period);
    }

    public AdxResult calculateADX(List<Candle> candles) {
        int period = config.getStrategy().getAdx().getPeriod();
        return calculateADXInternal(candles, period);
    }

    private AdxResult calculateADXInternal(List<Candle> candles, int period) {
        if (candles.size() < period * 2) {
            return AdxResult.builder().adx(0).plusDI(0).minusDI(0).isStrongTrend(false).build();
        }

        List<Double> plusDM = new ArrayList<>();
        List<Double> minusDM = new ArrayList<>();
        List<Double> tr = new ArrayList<>();

        for (int i = 1; i < candles.size(); i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);
            double upMove = curr.getHigh() - prev.getHigh();
            double downMove = prev.getLow() - curr.getLow();
            plusDM.add((upMove > downMove && upMove > 0) ? upMove : 0.0);
            minusDM.add((downMove > upMove && downMove > 0) ? downMove : 0.0);
            double trueRange = Math.max(curr.getHigh() - curr.getLow(),
                    Math.max(Math.abs(curr.getHigh() - prev.getClose()), Math.abs(curr.getLow() - prev.getClose())));
            tr.add(trueRange);
        }

        List<Double> dxSeries = new ArrayList<>();
        // Note: Simplified logic for compatibility, ideally use full wilder smoothing sequence
        double smoothTR = calculateWildersSmoothing(tr, period);
        double smoothPlusDM = calculateWildersSmoothing(plusDM, period);
        double smoothMinusDM = calculateWildersSmoothing(minusDM, period);
        double plusDI = (smoothPlusDM / smoothTR) * 100;
        double minusDI = (smoothMinusDM / smoothTR) * 100;

        // Return calculated values
        double dx = Math.abs(plusDI - minusDI) / (plusDI + minusDI) * 100;
        // In a real scenario, ADX is smoothed DX. For now returning DX as proxy if history short
        double adx = dx;

        boolean strong = adx >= config.getStrategy().getAdx().getThreshold();
        return AdxResult.builder().adx(adx).plusDI(plusDI).minusDI(minusDI).isStrongTrend(strong).build();
    }

    public double calculateRSI(List<Candle> candles) {
        return calculateRSIInternal(candles, config.getStrategy().getRsi().getPeriod());
    }

    private double calculateRSIInternal(List<Candle> candles, int period) {
        if (candles.size() < period + 1) return 50.0;
        double avgGain = 0.0, avgLoss = 0.0;
        for (int i = 1; i <= period; i++) {
            double change = candles.get(i).getClose() - candles.get(i - 1).getClose();
            if (change > 0) avgGain += change; else avgLoss += Math.abs(change);
        }
        avgGain /= period;
        avgLoss /= period;
        for (int i = period + 1; i < candles.size(); i++) {
            double change = candles.get(i).getClose() - candles.get(i - 1).getClose();
            if (change > 0) {
                avgGain = (avgGain * (period - 1) + change) / period;
                avgLoss = (avgLoss * (period - 1)) / period;
            } else {
                avgGain = (avgGain * (period - 1)) / period;
                avgLoss = (avgLoss * (period - 1) + Math.abs(change)) / period;
            }
        }
        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    public BollingerResult calculateBollingerBands(List<Candle> candles) {
        int period = config.getStrategy().getSqueeze().getBollingerPeriod();
        double stdDevMultiplier = config.getStrategy().getSqueeze().getBollingerDeviation();
        if (candles.size() < period) return BollingerResult.builder().build();
        List<Double> closes = candles.stream().map(Candle::getClose).collect(Collectors.toList());
        List<Double> subset = closes.subList(closes.size() - period, closes.size());
        double sma = subset.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = subset.stream().mapToDouble(val -> Math.pow(val - sma, 2)).sum() / period;
        double stdDev = Math.sqrt(variance);
        double upper = sma + (stdDevMultiplier * stdDev);
        double lower = sma - (stdDevMultiplier * stdDev);
        return BollingerResult.builder().middle(sma).upper(upper).lower(lower).bandwidth(upper - lower).build();
    }

    public KeltnerResult calculateKeltnerChannels(List<Candle> candles) {
        int period = config.getStrategy().getSqueeze().getKeltnerPeriod();
        double atrMultiplier = config.getStrategy().getSqueeze().getKeltnerAtrMultiplier();
        if (candles.size() < period) return KeltnerResult.builder().build();
        double ema = calculateEMAFromValues(candles.stream().map(Candle::getClose).collect(Collectors.toList()), period);
        double atr = calculateATR(candles, config.getStrategy().getAtr().getPeriod());
        return KeltnerResult.builder().middle(ema).upper(ema + (atrMultiplier * atr)).lower(ema - (atrMultiplier * atr)).bandwidth((ema + (atrMultiplier * atr)) - (ema - (atrMultiplier * atr))).build();
    }

    public MacdResult calculateMACD(List<Candle> candles) {
        int fast = config.getStrategy().getMacd().getFastPeriod();
        int slow = config.getStrategy().getMacd().getSlowPeriod();
        int signal = config.getStrategy().getMacd().getSignalPeriod();
        List<Double> closes = candles.stream().map(Candle::getClose).collect(Collectors.toList());
        List<Double> fastSeries = calculateEMASeries(closes, fast);
        List<Double> slowSeries = calculateEMASeries(closes, slow);
        List<Double> macdSeries = new ArrayList<>();
        int minSize = Math.min(fastSeries.size(), slowSeries.size());
        for (int i = 0; i < minSize; i++) {
            macdSeries.add(fastSeries.get(i) - slowSeries.get(i));
        }
        List<Double> signalSeries = calculateEMASeries(macdSeries, signal);
        double macdLine = macdSeries.isEmpty() ? 0 : macdSeries.get(macdSeries.size() - 1);
        double signalLine = signalSeries.isEmpty() ? 0 : signalSeries.get(signalSeries.size() - 1);
        return MacdResult.builder().macdLine(macdLine).signalLine(signalLine).histogram(macdLine - signalLine).isBullish(macdLine > signalLine && macdLine > 0).build();
    }

    // --- Helper Methods ---
    private double calculateWildersSmoothing(List<Double> values, int period) {
        if (values.isEmpty()) return 0.0;
        double sum = 0;
        for (int i = 0; i < period && i < values.size(); i++) sum += values.get(i);
        double smooth = sum / period;
        for (int i = period; i < values.size(); i++) smooth = ((smooth * (period - 1)) + values.get(i)) / period;
        return smooth;
    }

    private double calculateEMAFromValues(List<Double> values, int period) {
        if (values.isEmpty()) return 0.0;
        double k = 2.0 / (period + 1);
        double ema = values.get(0);
        for (int i = 1; i < values.size(); i++) ema = (values.get(i) * k) + (ema * (1 - k));
        return ema;
    }

    private List<Double> calculateEMASeries(List<Double> values, int period) {
        List<Double> emaSeries = new ArrayList<>();
        if (values.isEmpty()) return emaSeries;
        double k = 2.0 / (period + 1);
        double ema = values.get(0);
        emaSeries.add(ema);
        for (int i = 1; i < values.size(); i++) {
            ema = (values.get(i) * k) + (ema * (1 - k));
            emaSeries.add(ema);
        }
        return emaSeries;
    }
}