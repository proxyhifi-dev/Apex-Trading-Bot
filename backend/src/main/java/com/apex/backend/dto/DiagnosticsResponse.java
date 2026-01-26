package com.apex.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiagnosticsResponse {
    private boolean scannerEnabled;
    private int activeWatchlistStocks;
    private LastScannerRun lastScannerRun;
    private BotStateSummary botState;
    private boolean fyersConfigured;
    private boolean databaseReachable;

    @Data
    @Builder
    public static class LastScannerRun {
        private Long id;
        private String status;
        private String error;
    }

    @Data
    @Builder
    public static class BotStateSummary {
        private boolean running;
        private Boolean threadAlive;
        private String lastError;
    }
}
