package com.apex.backend.service;

import com.apex.backend.model.Candle;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SqueezeEngine {

    private static final int LOOKBACK_PERIOD = 20; // For Bollinger/Keltner

    public boolean isSqueeze(List<Candle> candles, double upperBB, double lowerBB, double upperKC, double lowerKC) {
        // Bollinger Bands must be INSIDE Keltner Channels to qualify as a squeeze
        boolean bbInsideKc = (upperBB < upperKC) && (lowerBB > lowerKC);
        return bbInsideKc;
    }

    public boolean isExpanding(double currentBandWidth, double prevBandWidth) {
        return currentBandWidth > prevBandWidth; // Volatility is returning
    }

    public double calculateBandWidth(double upper, double lower, double middle) {
        if (middle == 0) return 0;
        return (upper - lower) / middle;
    }
}