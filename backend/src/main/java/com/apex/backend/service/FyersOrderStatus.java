package com.apex.backend.service;

import java.math.BigDecimal;

public record FyersOrderStatus(
        String orderId,
        String status,
        Integer filledQuantity,
        BigDecimal averagePrice
) {}
