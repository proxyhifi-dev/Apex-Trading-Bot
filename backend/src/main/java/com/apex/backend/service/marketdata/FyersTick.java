package com.apex.backend.service.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

public record FyersTick(
        String symbol,
        BigDecimal lastTradedPrice,
        BigDecimal bidPrice,
        BigDecimal askPrice,
        Instant timestamp
) {}
