package com.apex.backend.dto;

import java.util.List;

public record StrategyHealthResponse(
        String status,
        boolean paused,
        List<String> reasons
) {}
