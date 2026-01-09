package com.apex.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AccountOverviewDTO {
    private String mode;
    private ProfileDTO profile;
    private FundsDTO funds;
    private Object holdings;
    private Object positions;
    private Double todayPnl;
    private LocalDateTime lastUpdatedAt;
    private String dataSource;

    @Data
    @Builder
    public static class ProfileDTO {
        private String name;
        private String brokerId;
    }

    @Data
    @Builder
    public static class FundsDTO {
        private Double cash;
        private Double used;
        private Double free;
    }
}
