package com.apex.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanRequest {

    @NotNull
    private Universe universe;

    private List<String> symbols;

    @NotBlank
    private String tf;

    @NotNull
    private Regime regime;

    private boolean dryRun;

    public enum Universe {
        NIFTY50,
        NIFTY200,
        CUSTOM
    }

    public enum Regime {
        AUTO,
        BULL,
        BEAR
    }
}
