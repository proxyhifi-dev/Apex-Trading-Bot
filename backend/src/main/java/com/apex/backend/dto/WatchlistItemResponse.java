package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistItemResponse {
    private String symbol;
    private Instant addedAt;
    private String status;
    private String failureReason;
}
