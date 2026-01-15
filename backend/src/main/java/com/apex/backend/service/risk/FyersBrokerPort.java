package com.apex.backend.service.risk;

import com.apex.backend.service.FyersAuthService;
import com.apex.backend.service.FyersService;
import com.apex.backend.util.MoneyUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FyersBrokerPort implements BrokerPort {

    private final FyersService fyersService;
    private final FyersAuthService fyersAuthService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<BrokerOrder> openOrders(Long userId) {
        try {
            Map<String, Object> response = fyersService.getOrdersForUser(userId);
            return parseOrders(response);
        } catch (Exception e) {
            log.warn("Failed to fetch FYERS orders for user {}", userId, e);
            return List.of();
        }
    }

    @Override
    public List<BrokerPosition> openPositions(Long userId) {
        try {
            Map<String, Object> response = fyersService.getPositionsForUser(userId);
            return parsePositions(response);
        } catch (Exception e) {
            log.warn("Failed to fetch FYERS positions for user {}", userId, e);
            return List.of();
        }
    }

    @Override
    public void cancelOrder(Long userId, String brokerOrderId) {
        try {
            String token = fyersAuthService.getFyersToken(userId);
            if (token == null || token.isBlank()) {
                return;
            }
            fyersService.cancelOrder(brokerOrderId, token);
        } catch (Exception e) {
            log.warn("Failed to cancel FYERS order {}", brokerOrderId, e);
        }
    }

    private List<BrokerOrder> parseOrders(Map<String, Object> response) {
        List<BrokerOrder> orders = new ArrayList<>();
        if (response == null || response.isEmpty()) {
            return orders;
        }
        JsonNode root = objectMapper.valueToTree(response);
        JsonNode dataNode = firstArray(root, "data", "orderBook", "d", "orders");
        if (dataNode == null) {
            return orders;
        }
        for (JsonNode node : dataNode) {
            String id = node.path("id").asText(node.path("order_id").asText(null));
            if (id == null || id.isBlank()) {
                continue;
            }
            String symbol = node.path("symbol").asText(node.path("symbolName").asText(null));
            String status = node.path("status").asText("UNKNOWN");
            Integer filledQty = node.path("filledQty").isMissingNode()
                    ? node.path("filled_qty").asInt(0)
                    : node.path("filledQty").asInt(0);
            BigDecimal avgPrice = MoneyUtils.bd(node.path("avgPrice").asDouble(node.path("avg_price").asDouble(0.0)));
            orders.add(new BrokerOrder(id, symbol, status, filledQty, avgPrice));
        }
        return orders;
    }

    private List<BrokerPosition> parsePositions(Map<String, Object> response) {
        List<BrokerPosition> positions = new ArrayList<>();
        if (response == null || response.isEmpty()) {
            return positions;
        }
        JsonNode root = objectMapper.valueToTree(response);
        JsonNode dataNode = firstArray(root, "data", "positions", "d");
        if (dataNode == null) {
            return positions;
        }
        for (JsonNode node : dataNode) {
            String symbol = node.path("symbol").asText(node.path("symbolName").asText(null));
            int netQty = node.path("netQty").asInt(node.path("net_qty").asInt(node.path("qty").asInt(0)));
            BigDecimal avgPrice = MoneyUtils.bd(node.path("avgPrice").asDouble(node.path("avg_price").asDouble(0.0)));
            positions.add(new BrokerPosition(symbol, netQty, avgPrice));
        }
        return positions;
    }

    private JsonNode firstArray(JsonNode root, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode node = root.path(field);
            if (node.isArray()) {
                return node;
            }
            if (node.has("data") && node.path("data").isArray()) {
                return node.path("data");
            }
        }
        if (root.has("data") && root.path("data").isArray()) {
            return root.path("data");
        }
        return null;
    }
}
