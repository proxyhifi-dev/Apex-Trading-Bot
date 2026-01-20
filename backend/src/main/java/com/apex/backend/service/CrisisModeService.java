package com.apex.backend.service;

import com.apex.backend.model.Candle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrisisModeService {

    private final FyersService fyersService;
    private final SystemGuardService systemGuardService;
    private final BroadcastService broadcastService;
    private final RiskEventService riskEventService;

    @Value("${crisis.enabled:true}")
    private boolean enabled;

    @Value("${crisis.index-symbol:NSE:NIFTY50-EQ}")
    private String indexSymbol;

    @Value("${crisis.drop-threshold-pct:5.0}")
    private double dropThresholdPct;

    @Value("${crisis.vix-threshold:30.0}")
    private double vixThreshold;

    @Value("${crisis.halt-duration-minutes:30}")
    private int haltDurationMinutes;

    @Value("${crisis.check-interval-ms:60000}")
    private long checkIntervalMs;

    @Scheduled(fixedDelayString = "${crisis.check-interval-ms:60000}")
    public void checkCrisisMode() {
        if (!enabled) {
            return;
        }

        if (systemGuardService.isCrisisModeActive()) {
            systemGuardService.clearCrisisModeIfExpired();
            return;
        }

        double indexChange = getIndexChangePct();
        if (indexChange <= -dropThresholdPct) {
            triggerCrisisMode("INDEX_DROP", String.format("%s dropped %.2f%%", indexSymbol, Math.abs(indexChange)));
            return;
        }

        double vixValue = getVixValue();
        if (vixValue > 0 && vixValue >= vixThreshold) {
            triggerCrisisMode("VIX_SPIKE", String.format("VIX at %.2f", vixValue));
        }
    }

    public boolean isCrisisModeActive() {
        return systemGuardService.isCrisisModeActive();
    }

    private double getIndexChangePct() {
        try {
            List<Candle> candles = fyersService.getHistoricalData(indexSymbol, 2, "D");
            if (candles.size() < 2) {
                return 0.0;
            }
            double previousClose = candles.get(candles.size() - 2).getClose();
            double latestClose = candles.get(candles.size() - 1).getClose();
            if (previousClose <= 0) {
                return 0.0;
            }
            return ((latestClose - previousClose) / previousClose) * 100.0;
        } catch (Exception e) {
            log.warn("Failed to evaluate index change for crisis mode: {}", e.getMessage());
            return 0.0;
        }
    }

    private double getVixValue() {
        return 0.0;
    }

    private void triggerCrisisMode(String reason, String detail) {
        Instant now = Instant.now();
        Instant until = now.plusSeconds(haltDurationMinutes * 60L);
        systemGuardService.setCrisisMode(true, reason, detail, now, until);
        riskEventService.record(0L, "CRISIS_MODE", reason, detail);

        broadcastService.broadcastBotStatus(new CrisisModeEvent(true, reason, detail, LocalDateTime.now(), until));
        log.warn("Crisis mode triggered: {} - {}", reason, detail);
    }

    public record CrisisModeEvent(boolean active, String reason, String detail, LocalDateTime timestamp, Instant until) {}
}
