package com.apex.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WatchlistEntryResponse {
    private Long id;
    private String symbol;
    private String exchange;
    private LocalDateTime createdAt;
}
