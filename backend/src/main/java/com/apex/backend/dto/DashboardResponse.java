package com.apex.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DashboardResponse {
    private BrokerStatusResponse brokerStatus;
    private AccountOverviewDTO accountOverview;
    private List<SignalDTO> latestSignals;
    private List<AccountOverviewDTO.PositionDTO> openPositions;
    private RiskStatusSummary riskStatus;
    private TodayPnlSummary todayPnl;
    private LocalDateTime generatedAt;

    @Data
    @Builder
    public static class RiskStatusSummary {
        private BigDecimal equity;
        private Integer openPositions;
    }

    @Data
    @Builder
    public static class TodayPnlSummary {
        private BigDecimal realized;
        private BigDecimal unrealized;
        private BigDecimal total;
    }
}
