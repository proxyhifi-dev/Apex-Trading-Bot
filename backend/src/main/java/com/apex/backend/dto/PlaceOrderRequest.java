package com.apex.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class PlaceOrderRequest {

    @NotBlank
    private String exchange;

    @NotBlank
    private String symbol;

    @NotNull
    private OrderSide side;

    @NotNull
    @Positive
    private Integer qty;

    @NotNull
    private OrderType orderType;

    @NotNull
    private ProductType productType;

    private BigDecimal price;

    private BigDecimal triggerPrice;

    @NotNull
    private Validity validity;

    private String tag;

    private String clientOrderId;

    private String remarks;

    public enum OrderSide {
        BUY, SELL
    }

    public enum OrderType {
        MARKET, LIMIT, SL, SL_M
    }

    public enum ProductType {
        INTRADAY, CNC, MARGIN
    }

    public enum Validity {
        DAY, IOC
    }
}
