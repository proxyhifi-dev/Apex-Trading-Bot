package com.apex.backend.service;

import com.apex.backend.dto.NormalizedOrderDTO;
import com.apex.backend.dto.OrderModifyRequest;
import com.apex.backend.dto.OrderResponse;
import com.apex.backend.dto.OrderValidationResult;
import com.apex.backend.dto.PlaceOrderRequest;
import com.apex.backend.exception.BadRequestException;
import com.apex.backend.exception.ConflictException;
import com.apex.backend.exception.FyersCircuitOpenException;
import com.apex.backend.model.InstrumentDefinition;
import com.apex.backend.model.OrderAudit;
import com.apex.backend.repository.OrderAuditRepository;
import com.apex.backend.util.MoneyUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderExecutionService {

    private final SettingsService settingsService;
    private final PaperOrderExecutionService paperOrderExecutionService;
    private final FyersService fyersService;
    private final InstrumentCacheService instrumentCacheService;
    private final TradingWindowService tradingWindowService;
    private final OrderAuditRepository orderAuditRepository;
    private final IdempotencyService idempotencyService;
    private final MetricsService metricsService;
    private final SystemGuardService systemGuardService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderValidationResult validate(Long userId, PlaceOrderRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Optional<InstrumentDefinition> instrument = instrumentCacheService.findBySymbol(request.getSymbol());
        if (instrument.isEmpty()) {
            errors.add("Unknown symbol: " + request.getSymbol());
        }
        if (request.getOrderType() == PlaceOrderRequest.OrderType.LIMIT && request.getPrice() == null) {
            errors.add("LIMIT order requires price");
        }
        if (request.getOrderType() == PlaceOrderRequest.OrderType.SL && request.getTriggerPrice() == null) {
            errors.add("SL order requires triggerPrice");
        }
        if (request.getOrderType() == PlaceOrderRequest.OrderType.SL_M && request.getTriggerPrice() == null) {
            errors.add("SL_M order requires triggerPrice");
        }
        instrument.ifPresent(def -> {
            if (def.getLotSize() != null && request.getQty() % def.getLotSize() != 0) {
                errors.add("Quantity must be multiple of lot size " + def.getLotSize());
            }
            if (def.getTickSize() != null && request.getPrice() != null) {
                BigDecimal remainder = request.getPrice().remainder(def.getTickSize());
                if (remainder.compareTo(BigDecimal.ZERO) != 0) {
                    warnings.add("Price not aligned to tick size " + def.getTickSize());
                }
            }
        });

        TradingWindowService.WindowDecision window = tradingWindowService.evaluate(Instant.now());
        if (!window.allowed()) {
            warnings.add("Outside trading window: " + window.reason());
        }

        NormalizedOrderDTO normalized = NormalizedOrderDTO.builder()
                .exchange(request.getExchange())
                .symbol(request.getSymbol())
                .side(request.getSide())
                .qty(request.getQty())
                .orderType(request.getOrderType())
                .productType(request.getProductType())
                .price(request.getPrice() != null ? MoneyUtils.scale(request.getPrice()) : null)
                .triggerPrice(request.getTriggerPrice() != null ? MoneyUtils.scale(request.getTriggerPrice()) : null)
                .validity(request.getValidity())
                .tag(request.getTag())
                .clientOrderId(request.getClientOrderId())
                .remarks(request.getRemarks())
                .build();

        return OrderValidationResult.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .normalizedOrder(normalized)
                .build();
    }

    @Transactional
    public OrderResponse placeOrder(Long userId, PlaceOrderRequest request) {
        String idempotencyKey = request.getClientOrderId();
        return idempotencyService.execute(userId, idempotencyKey, request, OrderResponse.class, () -> {
            if (systemGuardService.isTradingBlocked()) {
                throw new ConflictException("Trading halted by system guard");
            }
            OrderValidationResult validation = validate(userId, request);
            if (!validation.isValid()) {
                throw new BadRequestException(String.join("; ", validation.getErrors()));
            }
            boolean isPaper = settingsService.isPaperModeForUser(userId);
            List<String> warnings = validation.getWarnings();
            if (isPaper) {
                var paperOrder = paperOrderExecutionService.placeOrder(userId, request);
                OrderResponse response = OrderResponse.builder()
                        .status(paperOrder.getStatus())
                        .paperOrderId(paperOrder.getOrderId())
                        .submittedAt(Instant.now())
                        .warnings(warnings)
                        .build();
                metricsService.incrementOrdersPlaced();
                recordAudit(userId, "PLACE", "SUCCESS", null, paperOrder.getOrderId(), request, response);
                return response;
            }
            String brokerOrderId = null;
            try {
                brokerOrderId = placeLiveOrder(request, userId);
                OrderResponse response = OrderResponse.builder()
                        .status("SUBMITTED")
                        .brokerOrderId(brokerOrderId)
                        .submittedAt(Instant.now())
                        .warnings(warnings)
                        .build();
                metricsService.incrementOrdersPlaced();
                recordAudit(userId, "PLACE", "SUCCESS", brokerOrderId, null, request, response);
                return response;
            } catch (FyersCircuitOpenException ex) {
                recordAudit(userId, "PLACE", "FAILED", brokerOrderId, null, request, ex.getMessage());
                metricsService.recordReject("BROKER_CIRCUIT_OPEN");
                throw ex;
            } catch (Exception ex) {
                recordAudit(userId, "PLACE", "FAILED", brokerOrderId, null, request, ex.getMessage());
                metricsService.recordReject("BROKER_REJECTED");
                throw new ConflictException("Live order failed: " + ex.getMessage());
            }
        });
    }

    @Transactional
    public OrderResponse modifyOrder(Long userId, String orderId, OrderModifyRequest request) {
        boolean isPaper = settingsService.isPaperModeForUser(userId);
        if (isPaper) {
            var order = paperOrderExecutionService.modifyOrder(userId, Long.parseLong(orderId), request);
            OrderResponse response = OrderResponse.builder()
                    .status(order.getStatus())
                    .paperOrderId(order.getOrderId())
                    .submittedAt(Instant.now())
                    .warnings(List.of())
                    .build();
            recordAudit(userId, "MODIFY", "SUCCESS", null, order.getOrderId(), request, response);
            return response;
        }
        try {
            String brokerOrderId = fyersService.modifyOrder(orderId, request, userId);
            OrderResponse response = OrderResponse.builder()
                    .status("MODIFIED")
                    .brokerOrderId(brokerOrderId)
                    .submittedAt(Instant.now())
                    .warnings(List.of())
                    .build();
            recordAudit(userId, "MODIFY", "SUCCESS", brokerOrderId, null, request, response);
            return response;
        } catch (Exception ex) {
            recordAudit(userId, "MODIFY", "FAILED", orderId, null, request, ex.getMessage());
            throw new ConflictException("Order modification failed: " + ex.getMessage());
        }
    }

    @Transactional
    public OrderResponse cancelOrder(Long userId, String orderId) {
        boolean isPaper = settingsService.isPaperModeForUser(userId);
        if (isPaper) {
            var order = paperOrderExecutionService.cancelOrder(userId, Long.parseLong(orderId));
            OrderResponse response = OrderResponse.builder()
                    .status(order.getStatus())
                    .paperOrderId(order.getOrderId())
                    .submittedAt(Instant.now())
                    .warnings(List.of())
                    .build();
            recordAudit(userId, "CANCEL", "SUCCESS", null, order.getOrderId(), orderId, response);
            return response;
        }
        try {
            String brokerOrderId = fyersService.cancelOrder(orderId, null, userId);
            OrderResponse response = OrderResponse.builder()
                    .status("CANCELLED")
                    .brokerOrderId(brokerOrderId)
                    .submittedAt(Instant.now())
                    .warnings(List.of())
                    .build();
            recordAudit(userId, "CANCEL", "SUCCESS", brokerOrderId, null, orderId, response);
            return response;
        } catch (Exception ex) {
            recordAudit(userId, "CANCEL", "FAILED", orderId, null, orderId, ex.getMessage());
            throw new ConflictException("Order cancel failed: " + ex.getMessage());
        }
    }

    @Transactional
    public OrderResponse closePosition(Long userId, String symbol, Integer qty, String clientOrderId) {
        return idempotencyService.execute(userId, clientOrderId, symbol + ":" + qty, OrderResponse.class, () -> {
            if (!settingsService.isPaperModeForUser(userId)) {
                if (qty == null || qty <= 0) {
                    throw new BadRequestException("Quantity is required for live square-off");
                }
                String brokerOrderId = null;
                try {
                    brokerOrderId = fyersService.placeOrder(symbol, qty, "SELL", "MARKET", 0.0,
                            clientOrderId != null && !clientOrderId.isBlank() ? clientOrderId : java.util.UUID.randomUUID().toString(), userId);
                    OrderResponse response = OrderResponse.builder()
                            .status("SUBMITTED")
                            .brokerOrderId(brokerOrderId)
                            .submittedAt(Instant.now())
                            .warnings(List.of("Assumed SELL to close position"))
                            .build();
                    metricsService.incrementOrdersPlaced();
                    recordAudit(userId, "CLOSE", "SUCCESS", brokerOrderId, null, symbol, response);
                    return response;
                } catch (FyersCircuitOpenException ex) {
                    recordAudit(userId, "CLOSE", "FAILED", brokerOrderId, null, symbol, ex.getMessage());
                    metricsService.recordReject("BROKER_CIRCUIT_OPEN");
                    throw ex;
                } catch (Exception ex) {
                    recordAudit(userId, "CLOSE", "FAILED", brokerOrderId, null, symbol, ex.getMessage());
                    metricsService.recordReject("BROKER_REJECTED");
                    throw new ConflictException("Order close failed: " + ex.getMessage());
                }
            }
            var order = paperOrderExecutionService.closePosition(userId, symbol, qty == null ? 0 : qty);
            OrderResponse response = OrderResponse.builder()
                    .status(order.getStatus())
                    .paperOrderId(order.getOrderId())
                    .submittedAt(Instant.now())
                    .warnings(List.of())
                    .build();
            metricsService.incrementOrdersPlaced();
            recordAudit(userId, "CLOSE", "SUCCESS", null, order.getOrderId(), symbol, response);
            return response;
        });
    }

    private String placeLiveOrder(PlaceOrderRequest request, Long userId) {
        String type = request.getOrderType().name();
        double price = request.getPrice() != null ? request.getPrice().doubleValue() : 0.0;
        return fyersService.placeOrder(request.getSymbol(), request.getQty(), request.getSide().name(), type, price,
                request.getClientOrderId() != null ? request.getClientOrderId() : java.util.UUID.randomUUID().toString(), userId);
    }

    private void recordAudit(Long userId, String action, String status, String brokerOrderId, String paperOrderId,
                              Object request, Object response) {
        try {
            String requestPayload = request == null ? null : objectMapper.writeValueAsString(request);
            String responsePayload = response == null ? null : objectMapper.writeValueAsString(response);
            OrderAudit audit = OrderAudit.builder()
                    .userId(userId)
                    .action(action)
                    .status(status)
                    .brokerOrderId(brokerOrderId)
                    .paperOrderId(paperOrderId)
                    .requestPayload(requestPayload)
                    .responsePayload(responsePayload)
                    .createdAt(Instant.now())
                    .correlationId(org.slf4j.MDC.get("correlationId"))
                    .build();
            orderAuditRepository.save(audit);
        } catch (Exception e) {
            log.warn("Failed to record order audit: {}", e.getMessage());
        }
    }
}
