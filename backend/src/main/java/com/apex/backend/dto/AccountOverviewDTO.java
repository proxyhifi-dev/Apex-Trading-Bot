package com.apex.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AccountOverviewDTO {
    private String mode;
    private String dataSource;
    private LocalDateTime lastUpdatedAt;
    private ProfileDTO profile;
    private FundsDTO funds;
    private List<PositionDTO> positions;
    private List<HoldingDTO> holdings;
    private List<TradeDTO> recentTrades;

    @Data
    @Builder
    public static class ProfileDTO {
        private String name;
        private String brokerId;
        private String email;
    }

    @Data
    @Builder
    public static class FundsDTO {
        private BigDecimal cash;
        private BigDecimal used;
        private BigDecimal free;
        private BigDecimal totalPnl;
        private BigDecimal dayPnl;
    }

    @Data
    @Builder
    public static class PositionDTO {
        private String symbol;
        private String side;
        private Integer quantity;
        private BigDecimal avgPrice;
        private BigDecimal currentPrice;
        private BigDecimal pnl;
        private BigDecimal pnlPercent;
    }

    @Data
    @Builder
    public static class TradeDTO {
        private String symbol;
        private String side;
        private Integer quantity;
        private BigDecimal entryPrice;
        private BigDecimal exitPrice;
        private BigDecimal realizedPnl;
        private LocalDateTime entryTime;
        private LocalDateTime exitTime;
    }
}
