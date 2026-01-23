package com.apex.backend.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScannerRunRequest {
    @NotNull
    private UniverseType universeType;
    @Size(max = 100)
    private List<String> symbols;
    private Long watchlistId;
    private String index;
    private String timeframe;
    private String regime;
    private Long strategyId;
    private Map<String, Object> options;
    private boolean dryRun;
    private Mode mode;

    public enum UniverseType {
        WATCHLIST,
        SYMBOLS,
        INDEX
    }

    public enum Mode {
        PAPER,
        LIVE
    }
}
