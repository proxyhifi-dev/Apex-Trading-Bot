package com.apex.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BotStatusResponse {
    private String status;
    private LocalDateTime lastScanTime;
    private LocalDateTime nextScanTime;
    private int scannedStocks;
    private int totalStocks;
    private String strategyName;
    private String lastError;
    private Double portfolioValue;
    private Double availableEquity;
    private Double portfolioHeat;
    private Boolean tradingHalted;
}
