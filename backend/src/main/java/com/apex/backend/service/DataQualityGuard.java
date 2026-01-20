package com.apex.backend.service;

import com.apex.backend.config.DataQualityProperties;
import com.apex.backend.model.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DataQualityGuard {

    private final DataQualityProperties properties;

    public DataQualityResult validate(String timeframe, List<Candle> candles) {
        List<String> reasons = new ArrayList<>();
        if (candles == null || candles.size() < 2) {
            reasons.add("Insufficient candles for data quality validation");
            return new DataQualityResult(false, reasons, DataQualityIssue.INSUFFICIENT);
        }

        LocalDateTime latest = candles.get(candles.size() - 1).getTimestamp();
        if (latest != null) {
            long ageSeconds = Duration.between(latest, LocalDateTime.now()).getSeconds();
            if (ageSeconds > properties.getMaxStaleSeconds()) {
                reasons.add("DATA_STALE: " + ageSeconds + "s old");
                return new DataQualityResult(false, reasons, DataQualityIssue.STALE);
            }
        }

        long expectedMinutes = parseTimeframeMinutes(timeframe);
        int missingCount = 0;
        for (int i = 1; i < candles.size(); i++) {
            LocalDateTime prev = candles.get(i - 1).getTimestamp();
            LocalDateTime curr = candles.get(i).getTimestamp();
            if (prev == null || curr == null) {
                missingCount++;
                continue;
            }
            long minutes = Duration.between(prev, curr).toMinutes();
            if (expectedMinutes > 0 && minutes > expectedMinutes * properties.getMaxGapMultiplier()) {
                missingCount++;
            }
        }
        if (missingCount > properties.getMaxMissingCandles()) {
            reasons.add("DATA_GAP: " + missingCount + " missing candles");
            return new DataQualityResult(false, reasons, DataQualityIssue.GAP);
        }

        double outlierPct = properties.getOutlierPct();
        for (int i = 1; i < candles.size(); i++) {
            double prevClose = candles.get(i - 1).getClose();
            double close = candles.get(i).getClose();
            if (prevClose <= 0) {
                continue;
            }
            double pctChange = Math.abs(close - prevClose) / prevClose;
            if (pctChange > outlierPct) {
                reasons.add("OUTLIER: " + pctChange);
                return new DataQualityResult(false, reasons, DataQualityIssue.OUTLIER);
            }
        }
        return new DataQualityResult(true, reasons, null);
    }

    private long parseTimeframeMinutes(String timeframe) {
        if (timeframe == null || timeframe.isBlank()) {
            return 0;
        }
        if ("D".equalsIgnoreCase(timeframe)) {
            return 1440;
        }
        try {
            return Long.parseLong(timeframe);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public enum DataQualityIssue {
        STALE,
        GAP,
        OUTLIER,
        INSUFFICIENT
    }

    public record DataQualityResult(boolean allowed, List<String> reasons, DataQualityIssue issue) {}
}
