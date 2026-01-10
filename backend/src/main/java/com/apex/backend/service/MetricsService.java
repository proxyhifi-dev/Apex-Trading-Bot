package com.apex.backend.service;

import com.apex.backend.dto.MetricsSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class MetricsService {

    private final AtomicLong ordersPlaced = new AtomicLong();
    private final AtomicLong websocketPublishes = new AtomicLong();
    private final AtomicLong brokerFailures = new AtomicLong();
    private final ConcurrentHashMap<String, AtomicLong> rejectsByReason = new ConcurrentHashMap<>();
    private volatile double totalPnl;
    private volatile double maxDrawdown;
    private volatile double riskUsage;

    public void incrementOrdersPlaced() {
        ordersPlaced.incrementAndGet();
    }

    public void incrementWebsocketPublishes() {
        websocketPublishes.incrementAndGet();
    }

    public void incrementBrokerFailures() {
        brokerFailures.incrementAndGet();
    }

    public void recordReject(String reason) {
        rejectsByReason.computeIfAbsent(reason, key -> new AtomicLong()).incrementAndGet();
    }

    public void updatePnl(double pnl) {
        totalPnl += pnl;
        maxDrawdown = Math.min(maxDrawdown, totalPnl);
    }

    public void updateRiskUsage(double riskUsage) {
        this.riskUsage = riskUsage;
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
