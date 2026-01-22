package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistResponse {
    private Long id;
    private String name;
    private boolean isDefault;
    private int maxItems;
    private int itemCount;
    private Instant createdAt;
    private Instant updatedAt;
    private List<WatchlistItemResponse> items;
}
