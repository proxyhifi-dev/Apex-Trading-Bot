package com.apex.backend.service.marketdata;

import java.math.BigDecimal;

public record FyersDepthLevel(
        BigDecimal price,
        BigDecimal quantity
) {}
