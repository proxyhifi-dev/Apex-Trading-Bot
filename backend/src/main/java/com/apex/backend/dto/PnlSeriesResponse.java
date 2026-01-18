package com.apex.backend.dto;

import java.util.List;

public record PnlSeriesResponse(String type, String granularity, List<PnlSeriesPoint> series) {}
