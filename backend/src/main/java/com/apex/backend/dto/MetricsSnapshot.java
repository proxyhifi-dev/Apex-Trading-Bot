package com.apex.backend.dto;

import java.util.Map;

public record MetricsSnapshot(
        long ordersPlaced,
        Map<String, Long> rejectsByReason,
        double totalPnl,
        double maxDrawdown,
        double riskUsage,
        long websocketPublishes,
        long brokerFailures
) {}
