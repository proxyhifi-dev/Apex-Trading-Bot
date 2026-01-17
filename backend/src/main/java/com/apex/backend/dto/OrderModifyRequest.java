package com.apex.backend.dto;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderModifyRequest {

    @Positive
    private Integer qty;

    private BigDecimal price;

    private BigDecimal triggerPrice;

    private PlaceOrderRequest.OrderType orderType;

    private PlaceOrderRequest.Validity validity;

    private String remarks;
}
