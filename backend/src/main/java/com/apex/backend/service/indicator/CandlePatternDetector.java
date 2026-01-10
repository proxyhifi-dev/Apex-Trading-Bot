package com.apex.backend.service.indicator;

import com.apex.backend.dto.PatternResult;
import com.apex.backend.model.Candle;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CandlePatternDetector {

    public PatternResult detect(List<Candle> candles) {
        if (candles == null || candles.size() < 2) {
            return new PatternResult("NONE", false, 0.0);
        }
        Candle last = candles.get(candles.size() - 1);
        Candle prev = candles.get(candles.size() - 2);

        PatternResult engulfing = detectEngulfing(prev, last);
        if (!"NONE".equals(engulfing.type())) {
            return engulfing;
        }
        PatternResult hammer = detectHammer(last);
        if (!"NONE".equals(hammer.type())) {
            return hammer;
        }
        PatternResult pinBar = detectPinBar(last);
        if (!"NONE".equals(pinBar.type())) {
            return pinBar;
        }
        PatternResult doji = detectDoji(last);
        if (!"NONE".equals(doji.type())) {
            return doji;
        }
        if (candles.size() >= 3) {
            Candle first = candles.get(candles.size() - 3);
            PatternResult star = detectStar(first, prev, last);
            if (!"NONE".equals(star.type())) {
                return star;
            }
        }
        return new PatternResult("NONE", false, 0.0);
    }

    private PatternResult detectEngulfing(Candle prev, Candle curr) {
        double prevBody = Math.abs(prev.getClose() - prev.getOpen());
        double currBody = Math.abs(curr.getClose() - curr.getOpen());
        if (currBody == 0 || prevBody == 0) {
            return new PatternResult("NONE", false, 0.0);
        }
        boolean bullish = prev.getClose() < prev.getOpen()
                && curr.getClose() > curr.getOpen()
                && curr.getClose() >= prev.getOpen()
                && curr.getOpen() <= prev.getClose();
        boolean bearish = prev.getClose() > prev.getOpen()
                && curr.getClose() < curr.getOpen()
                && curr.getOpen() >= prev.getClose()
                && curr.getClose() <= prev.getOpen();
        if (bullish || bearish) {
            double strength = Math.min(1.0, currBody / (prevBody * 1.2));
            return new PatternResult("ENGULFING", bullish, strength);
        }
        return new PatternResult("NONE", false, 0.0);
    }

    private PatternResult detectHammer(Candle candle) {
        double body = Math.abs(candle.getClose() - candle.getOpen());
        double range = candle.getHigh() - candle.getLow();
        if (range == 0) {
            return new PatternResult("NONE", false, 0.0);
        }
        double upperWick = candle.getHigh() - Math.max(candle.getClose(), candle.getOpen());
        double lowerWick = Math.min(candle.getClose(), candle.getOpen()) - candle.getLow();
        boolean isHammer = lowerWick >= body * 2 && upperWick <= body * 0.5;
        boolean isInverted = upperWick >= body * 2 && lowerWick <= body * 0.5;
        if (isHammer) {
            double strength = Math.min(1.0, lowerWick / range);
            return new PatternResult("HAMMER", true, strength);
        }
        if (isInverted) {
            double strength = Math.min(1.0, upperWick / range);
            return new PatternResult("INVERTED_HAMMER", true, strength);
        }
        return new PatternResult("NONE", false, 0.0);
    }

    private PatternResult detectPinBar(Candle candle) {
        double body = Math.abs(candle.getClose() - candle.getOpen());
        double range = candle.getHigh() - candle.getLow();
        if (range == 0) {
            return new PatternResult("NONE", false, 0.0);
        }
        double upperWick = candle.getHigh() - Math.max(candle.getClose(), candle.getOpen());
        double lowerWick = Math.min(candle.getClose(), candle.getOpen()) - candle.getLow();
        if (upperWick >= body * 2.5 && upperWick > lowerWick) {
            double strength = Math.min(1.0, upperWick / range);
            return new PatternResult("PIN_BAR", false, strength);
        }
        if (lowerWick >= body * 2.5 && lowerWick > upperWick) {
            double strength = Math.min(1.0, lowerWick / range);
            return new PatternResult("PIN_BAR", true, strength);
        }
        return new PatternResult("NONE", false, 0.0);
    }

    private PatternResult detectDoji(Candle candle) {
        double body = Math.abs(candle.getClose() - candle.getOpen());
        double range = candle.getHigh() - candle.getLow();
        if (range == 0) {
            return new PatternResult("NONE", false, 0.0);
        }
        if (body / range <= 0.1) {
            return new PatternResult("DOJI", candle.getClose() >= candle.getOpen(), 0.5);
        }
        return new PatternResult("NONE", false, 0.0);
    }

    private PatternResult detectStar(Candle first, Candle middle, Candle last) {
        boolean firstBearish = first.getClose() < first.getOpen();
        boolean firstBullish = first.getClose() > first.getOpen();
        boolean lastBullish = last.getClose() > last.getOpen();
        boolean lastBearish = last.getClose() < last.getOpen();
        double firstMid = (first.getOpen() + first.getClose()) / 2.0;
        double lastBody = Math.abs(last.getClose() - last.getOpen());
        double middleBody = Math.abs(middle.getClose() - middle.getOpen());
        if (firstBearish && lastBullish && middleBody < lastBody * 0.5 && last.getClose() > firstMid) {
            return new PatternResult("MORNING_STAR", true, 0.8);
        }
        if (firstBullish && lastBearish && middleBody < lastBody * 0.5 && last.getClose() < firstMid) {
            return new PatternResult("EVENING_STAR", false, 0.8);
        }
        return new PatternResult("NONE", false, 0.0);
    }
}
