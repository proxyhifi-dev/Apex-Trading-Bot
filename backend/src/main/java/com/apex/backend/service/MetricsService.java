package com.apex.backend.service;

import com.apex.backend.dto.MetricsSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetricsService {

    private final MeterRegistry meterRegistry;

    private final AtomicLong ordersPlaced = new AtomicLong();
    private final AtomicLong websocketPublishes = new AtomicLong();
    private final AtomicLong brokerFailures = new AtomicLong();
    private final ConcurrentHashMap<String, AtomicLong> rejectsByReason = new ConcurrentHashMap<>();
    private final AtomicReference<Double> pnlDaily = new AtomicReference<>(0.0);
    private final AtomicReference<Double> drawdownCurrent = new AtomicReference<>(0.0);
    private volatile double totalPnl;
    private volatile double maxDrawdown;
    private volatile double riskUsage;

    private Counter ordersPlacedCounter;
    private Counter brokerErrorsCounter;
    private Counter ordersFilledCounter;
    private Counter stopLossFailuresCounter;
    private Counter emergencyFlattensCounter;

    @jakarta.annotation.PostConstruct
    void init() {
        ordersPlacedCounter = Counter.builder("orders_placed_total").register(meterRegistry);
        brokerErrorsCounter = Counter.builder("broker_errors_total").register(meterRegistry);
        ordersFilledCounter = Counter.builder("orders_filled_total").register(meterRegistry);
        stopLossFailuresCounter = Counter.builder("stop_loss_failures_total").register(meterRegistry);
        emergencyFlattensCounter = Counter.builder("emergency_flattens_total").register(meterRegistry);
        Gauge.builder("pnl_daily", pnlDaily, value -> value.get()).register(meterRegistry);
        Gauge.builder("drawdown_current", drawdownCurrent, value -> value.get()).register(meterRegistry);
    }

    public void incrementOrdersPlaced() {
        ordersPlaced.incrementAndGet();
        if (ordersPlacedCounter != null) {
            ordersPlacedCounter.increment();
        }
    }

    public void incrementWebsocketPublishes() {
        websocketPublishes.incrementAndGet();
    }

    public void incrementBrokerFailures() {
        brokerFailures.incrementAndGet();
        if (brokerErrorsCounter != null) {
            brokerErrorsCounter.increment();
        }
    }

    public void recordOrderFilled() {
        if (ordersFilledCounter != null) {
            ordersFilledCounter.increment();
        }
    }

    public void recordStopLossFailure() {
        if (stopLossFailuresCounter != null) {
            stopLossFailuresCounter.increment();
        }
    }

    public void recordEmergencyFlatten() {
        if (emergencyFlattensCounter != null) {
            emergencyFlattensCounter.increment();
        }
    }

    public void recordReject(String reason) {
        rejectsByReason.computeIfAbsent(reason, key -> new AtomicLong()).incrementAndGet();
        Counter.builder("orders_rejected_total")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    public void updatePnl(double pnl) {
        totalPnl += pnl;
        maxDrawdown = Math.min(maxDrawdown, totalPnl);
        pnlDaily.set(totalPnl);
        drawdownCurrent.set(maxDrawdown);
    }

    public void updateRiskUsage(double riskUsage) {
        this.riskUsage = riskUsage;
    }

    public void recordStrategySignal(String reason, double score) {
        String bucket = score < 50 ? "0-49" : score < 70 ? "50-69" : score < 85 ? "70-84" : "85+";
        Counter.builder("strategy_signals_total")
                .tag("reason", reason == null ? "unknown" : reason)
                .tag("score_bucket", bucket)
                .register(meterRegistry)
                .increment();
    }

    public MetricsSnapshot snapshot() {
        Map<String, Long> rejectCounts = rejectsByReason.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));
        return new MetricsSnapshot(
                ordersPlaced.get(),
                rejectCounts,
                totalPnl,
                maxDrawdown,
                riskUsage,
                websocketPublishes.get(),
                brokerFailures.get()
        );
    }
}
