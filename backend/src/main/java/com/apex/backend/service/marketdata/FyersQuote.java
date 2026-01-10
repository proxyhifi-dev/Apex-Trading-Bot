package com.apex.backend.service.marketdata;

import java.math.BigDecimal;

public record FyersQuote(
        String symbol,
        BigDecimal lastTradedPrice,
        BigDecimal bidPrice,
        BigDecimal askPrice
) {}
