package com.apex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedOrderDTO {

    private String exchange;
    private String symbol;
    private PlaceOrderRequest.OrderSide side;
    private Integer qty;
    private PlaceOrderRequest.OrderType orderType;
    private PlaceOrderRequest.ProductType productType;
    private BigDecimal price;
    private BigDecimal triggerPrice;
    private PlaceOrderRequest.Validity validity;
    private String tag;
    private String clientOrderId;
    private String remarks;
}
