package com.apex.backend.service.risk;

import java.math.BigDecimal;
import java.util.List;

public interface BrokerPort {

    List<BrokerOrder> openOrders(Long userId);

    List<BrokerPosition> openPositions(Long userId);

    void cancelOrder(Long userId, String brokerOrderId);

    record BrokerOrder(String orderId, String symbol, String status, Integer filledQty, BigDecimal averagePrice) {}

    record BrokerPosition(String symbol, Integer netQty, BigDecimal averagePrice) {}
}
