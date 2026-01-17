package com.apex.backend.dto;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosePositionRequest {

    @Positive
    private Integer qty;

    @Builder.Default
    private PlaceOrderRequest.OrderType orderType = PlaceOrderRequest.OrderType.MARKET;
}
