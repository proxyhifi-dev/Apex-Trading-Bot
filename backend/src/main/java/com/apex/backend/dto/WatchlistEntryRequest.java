package com.apex.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WatchlistEntryRequest {
    @NotBlank
    private String symbol;

    @NotBlank
    private String exchange;
}
