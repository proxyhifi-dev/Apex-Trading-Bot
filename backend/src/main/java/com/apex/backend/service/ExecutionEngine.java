package com.apex.backend.service;

import com.apex.backend.config.ExecutionProperties;
import com.apex.backend.model.Candle;
import com.apex.backend.model.OrderIntent;
import com.apex.backend.model.OrderState;
import com.apex.backend.repository.OrderIntentRepository;
import com.apex.backend.service.ExecutionCostModel.ExecutionRequest;
import com.apex.backend.service.ExecutionCostModel.ExecutionSide;
import com.apex.backend.service.marketdata.FyersMarketDataClient;
import com.apex.backend.service.marketdata.FyersQuote;
import com.apex.backend.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionEngine {

    private final FyersService fyersService;
    private final FyersAuthService fyersAuthService;
    private final FyersMarketDataClient marketDataClient;
    private final ExecutionCostModel executionCostModel;
    private final OrderIntentRepository orderIntentRepository;
    private final MetricsService metricsService;
    private final RiskGatekeeper riskGatekeeper;
    private final ExecutionProperties executionProperties;
    private final BroadcastService broadcastService;
    private final AsyncDelayService asyncDelayService;

    @Value("${execution.poll.max-attempts:12}")
    private int maxPollAttempts;

    @Value("${execution.poll.delay-ms:1000}")
    private long pollDelayMs;

    @Transactional
    public ExecutionResult execute(ExecutionRequestPayload request) {
        String clientOrderId = request.clientOrderId() == null || request.clientOrderId().isBlank()
                ? UUID.randomUUID().toString()
                : request.clientOrderId();
        String correlationId = UUID.randomUUID().toString();
        org.slf4j.MDC.put("orderId", clientOrderId);
        org.slf4j.MDC.put("requestId", clientOrderId);
        org.slf4j.MDC.put("correlationId", correlationId);
        if (request.tradeId() != null) {
            org.slf4j.MDC.put("tradeId", request.tradeId().toString());
        }
        try {
            Optional<OrderIntent> existing = orderIntentRepository.findByClientOrderId(clientOrderId);
            if (existing.isPresent()) {
                OrderIntent existingIntent = existing.get();
                return toResult(existingIntent, ExecutionStatus.from(existingIntent.getOrderState().name()));
            }

            RiskGatekeeper.RiskGateDecision riskDecision = riskGatekeeper.evaluate(new RiskGatekeeper.RiskGateRequest(
                    request.userId(),
                    request.symbol(),
                    request.quantity(),
                    request.paper(),
                    request.referencePrice(),
                    request.stopLoss(),
                    request.exitOrder()
            ));
            if (!riskDecision.allowed()) {
                OrderIntent rejectedIntent = orderIntentRepository.save(OrderIntent.builder()
                        .clientOrderId(clientOrderId)
                        .userId(request.userId())
                        .symbol(request.symbol())
                        .side(request.side().name())
                        .quantity(request.quantity())
                        .status("REJECTED")
                        .orderState(OrderState.CREATED)
                        .correlationId(correlationId)
                        .signalId(request.signalId())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build());
                rejectedIntent.transitionTo(OrderState.REJECTED);
                orderIntentRepository.save(rejectedIntent);
                
                String reasonCode = riskDecision.reason() != null ? riskDecision.reason().name() : "RISK_REJECTED";
                metricsService.recordReject(reasonCode);
                log.info("Order rejected: {} reason: {} threshold: {} current: {}", 
                    clientOrderId, reasonCode, riskDecision.threshold(), riskDecision.currentValue());
                
                // Broadcast reject event
                broadcastService.broadcastReject(new BroadcastService.RejectEvent(
                    reasonCode,
                    riskDecision.threshold(),
                    riskDecision.currentValue(),
                    riskDecision.symbol(),
                    riskDecision.signalId(),
                    clientOrderId
                ));
                
                return new ExecutionResult(clientOrderId, null, ExecutionStatus.REJECTED, 0, null, reasonCode);
            }

            OrderIntent intent = OrderIntent.builder()
                    .clientOrderId(clientOrderId)
                    .userId(request.userId())
                    .symbol(request.symbol())
                    .side(request.side().name())
                    .quantity(request.quantity())
                    .status("PENDING")
                    .orderState(OrderState.CREATED)
                    .correlationId(correlationId)
                    .signalId(request.signalId())
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusSeconds(executionProperties.getEntryTimeoutSeconds()))
                    .build();
            intent = orderIntentRepository.save(intent);

            String token = request.paper() ? null : fyersAuthService.getFyersToken(request.userId());
            FyersQuote quote = marketDataClient.getQuote(request.symbol(), token).orElse(null);
            ExecutionCostModel.ExecutionRequest costRequest = buildCostRequest(request, quote, request.referencePrice());
            executionCostModel.estimateCost(clientOrderId, costRequest);

            if (request.paper()) {
                ExecutionCostModel.ExecutionEstimate estimate = executionCostModel.estimateExecution(costRequest);
                intent.transitionTo(OrderState.FILLED);
                intent.setFilledQuantity(request.quantity());
                intent.setAveragePrice(MoneyUtils.bd(estimate.effectivePrice()));
                intent.setBrokerOrderId("PAPER-" + System.currentTimeMillis());
                orderIntentRepository.save(intent);
                ExecutionCostModel.ExecutionRequest realizedRequest = buildCostRequest(request, quote, estimate.effectivePrice());
                double realizedCost = executionCostModel.calculateAllInCost(realizedRequest);
                executionCostModel.updateRealizedCost(clientOrderId, realizedCost, "PAPER");
                metricsService.incrementOrdersPlaced();
                log.info("Paper order filled: {} correlationId: {}", clientOrderId, correlationId);
                return toResult(intent, ExecutionStatus.FILLED);
            }

            if (token == null || token.isBlank()) {
                intent.transitionTo(OrderState.REJECTED);
                orderIntentRepository.save(intent);
                metricsService.recordReject("NO_BROKER_TOKEN");
                log.warn("Order rejected - no broker token: {} correlationId: {}", clientOrderId, correlationId);
                return new ExecutionResult(clientOrderId, null, ExecutionStatus.REJECTED, 0, null, "NO_BROKER_TOKEN");
            }

            // Transition to SENT state
            intent.transitionTo(OrderState.SENT);
            intent.setSentAt(LocalDateTime.now());
            orderIntentRepository.save(intent);
            log.info("Order sent to broker: {} correlationId: {}", clientOrderId, correlationId);

            String orderId = fyersService.placeOrder(
                    request.symbol(),
                    request.quantity(),
                    request.side().name(),
                    request.orderType().name(),
                    request.limitPrice() == null ? 0.0 : request.limitPrice(),
                    clientOrderId
            );
            intent.setBrokerOrderId(orderId);
            intent.transitionTo(OrderState.ACKED);
            intent.setAckedAt(LocalDateTime.now());
            intent.setLastBrokerStatus("ACKED");
            orderIntentRepository.save(intent);
            log.info("Order ACKED by broker: {} brokerOrderId: {} correlationId: {}", clientOrderId, orderId, correlationId);

            ExecutionResult result = pollUntilTerminal(intent, token);
            if (result.status() == ExecutionStatus.FILLED) {
                metricsService.incrementOrdersPlaced();
                double fillPrice = result.averagePrice() != null ? result.averagePrice().doubleValue() : request.referencePrice();
                ExecutionCostModel.ExecutionRequest realizedRequest = buildCostRequest(request, quote, fillPrice);
                double realizedCost = executionCostModel.calculateAllInCost(realizedRequest);
                executionCostModel.updateRealizedCost(clientOrderId, realizedCost, result.brokerOrderId());
            } else if (result.status() == ExecutionStatus.REJECTED) {
                metricsService.recordReject("BROKER_REJECTED");
            }
            return result;
        } finally {
            org.slf4j.MDC.remove("orderId");
            org.slf4j.MDC.remove("requestId");
            org.slf4j.MDC.remove("correlationId");
            org.slf4j.MDC.remove("tradeId");
        }
    }

    private ExecutionResult pollUntilTerminal(OrderIntent intent, String token) {
        int attempts = 0;
        ExecutionResult lastResult = toResult(intent, ExecutionStatus.PENDING);
        while (attempts < maxPollAttempts) {
            // Check timeout
            if (intent.getExpiresAt() != null && LocalDateTime.now().isAfter(intent.getExpiresAt())) {
                log.warn("Order timeout: {} correlationId: {}", intent.getClientOrderId(), intent.getCorrelationId());
                intent.transitionTo(OrderState.EXPIRED);
                intent.setLastBrokerStatus("TIMEOUT");
                orderIntentRepository.save(intent);
                
                if (executionProperties.isCancelOnTimeout()) {
                    try {
                        fyersService.cancelOrder(intent.getBrokerOrderId(), token);
                        intent.transitionTo(OrderState.CANCEL_REQUESTED);
                        orderIntentRepository.save(intent);
                        log.info("Cancel requested for timed-out order: {}", intent.getClientOrderId());
                    } catch (Exception e) {
                        log.error("Failed to cancel timed-out order: {}", intent.getClientOrderId(), e);
                    }
                }
                
                broadcastService.broadcastOrders(List.of(intent));
                return toResult(intent, ExecutionStatus.EXPIRED);
            }
            
            attempts++;
            Optional<FyersOrderStatus> statusOpt = fyersService.getOrderDetails(intent.getBrokerOrderId(), token);
            if (statusOpt.isEmpty()) {
                sleep();
                continue;
            }
            FyersOrderStatus status = statusOpt.get();
            intent.setLastBrokerStatus(status.status());
            ExecutionStatus execStatus = ExecutionStatus.from(status.status());
            
            if (execStatus == ExecutionStatus.PARTIALLY_FILLED) {
                applyPartialFill(intent, status, token);
                lastResult = toResult(intent, execStatus);
                sleep();
                continue;
            }
            if (execStatus.isTerminal()) {
                if (execStatus == ExecutionStatus.FILLED) {
                    applyFinalFill(intent, status);
                } else if (execStatus == ExecutionStatus.REJECTED) {
                    intent.transitionTo(OrderState.REJECTED);
                    orderIntentRepository.save(intent);
                } else if (execStatus == ExecutionStatus.CANCELLED) {
                    intent.transitionTo(OrderState.CANCELLED);
                    orderIntentRepository.save(intent);
                }
                log.info("Order terminal state: {} state: {} correlationId: {}", 
                    intent.getClientOrderId(), execStatus, intent.getCorrelationId());
                broadcastService.broadcastOrders(List.of(intent));
                return toResult(intent, execStatus);
            }
            lastResult = toResult(intent, execStatus);
            sleep();
        }
        
        // Max attempts reached
        if (intent.getExpiresAt() != null && LocalDateTime.now().isAfter(intent.getExpiresAt())) {
            intent.transitionTo(OrderState.EXPIRED);
        } else {
            intent.transitionTo(OrderState.UNKNOWN);
        }
        orderIntentRepository.save(intent);
        log.warn("Order polling exhausted: {} final state: {} correlationId: {}", 
            intent.getClientOrderId(), intent.getOrderState(), intent.getCorrelationId());
        return toResult(intent, ExecutionStatus.UNKNOWN);
    }

    private void applyPartialFill(OrderIntent intent, FyersOrderStatus status, String token) {
        if (status.filledQuantity() != null && status.filledQuantity() > 0) {
            intent.setFilledQuantity(status.filledQuantity());
        }
        if (status.averagePrice() != null) {
            intent.setAveragePrice(status.averagePrice());
        }
        intent.transitionTo(OrderState.PART_FILLED);
        intent.setLastBrokerStatus(status.status());
        orderIntentRepository.save(intent);
        
        // Calculate fill percentage
        int filledQty = intent.getFilledQuantity() != null ? intent.getFilledQuantity() : 0;
        int totalQty = intent.getQuantity();
        double fillPct = (filledQty * 100.0) / totalQty;
        
        ExecutionProperties.PartialFillPolicy policy = executionProperties.getPartialFill().getPolicy();
        if (fillPct < executionProperties.getPartialFill().getMinFillPct()) {
            policy = ExecutionProperties.PartialFillPolicy.CANCEL_AND_FLATTEN;
            log.warn("Partial fill below threshold: {}% (min: {}%) for order: {}", 
                fillPct, executionProperties.getPartialFill().getMinFillPct(), intent.getClientOrderId());
        }
        
        log.info("Partial fill: {}% filled, policy: {}, order: {} correlationId: {}", 
            fillPct, policy, intent.getClientOrderId(), intent.getCorrelationId());
        
        switch (policy) {
            case CANCEL_REMAIN, CANCEL_AND_FLATTEN -> {
                cancelRemainingQuantity(intent, filledQty, token);
                broadcastService.broadcastOrders(List.of(intent));
            }
            case KEEP_OPEN -> {
                // Continue polling (existing behavior)
                broadcastService.broadcastOrders(List.of(intent));
            }
        }
    }
    
    private void cancelRemainingQuantity(OrderIntent intent, int filledQty, String token) {
        int remainingQty = intent.getQuantity() - filledQty;
        if (remainingQty <= 0) return;
        
        log.info("Canceling remaining quantity: {} for order: {} correlationId: {}", 
            remainingQty, intent.getClientOrderId(), intent.getCorrelationId());
        
        intent.transitionTo(OrderState.CANCEL_REQUESTED);
        orderIntentRepository.save(intent);
        
        try {
            fyersService.cancelOrder(intent.getBrokerOrderId(), token);
            log.info("Cancel request sent for order: {} correlationId: {}", 
                intent.getClientOrderId(), intent.getCorrelationId());
        } catch (Exception e) {
            log.error("Failed to cancel remaining qty for order: {} correlationId: {}", 
                intent.getClientOrderId(), intent.getCorrelationId(), e);
            intent.transitionTo(OrderState.UNKNOWN);
            orderIntentRepository.save(intent);
        }
    }

    private void applyFinalFill(OrderIntent intent, FyersOrderStatus status) {
        if (status.filledQuantity() != null && status.filledQuantity() > 0) {
            intent.setFilledQuantity(status.filledQuantity());
        } else {
            intent.setFilledQuantity(intent.getQuantity());
        }
        if (status.averagePrice() != null && status.averagePrice().compareTo(BigDecimal.ZERO) > 0) {
            intent.setAveragePrice(status.averagePrice());
        }
        intent.transitionTo(OrderState.FILLED);
        intent.setLastBrokerStatus(status.status());
        orderIntentRepository.save(intent);
        metricsService.recordOrderFilled();
        log.info("Order filled: {} filledQty: {} avgPrice: {} correlationId: {}", 
            intent.getClientOrderId(), intent.getFilledQuantity(), intent.getAveragePrice(), intent.getCorrelationId());
    }

    private ExecutionRequest buildCostRequest(ExecutionRequestPayload request, FyersQuote quote, double price) {
        Double bid = quote != null && quote.bidPrice() != null ? quote.bidPrice().doubleValue() : null;
        Double ask = quote != null && quote.askPrice() != null ? quote.askPrice().doubleValue() : null;
        return new ExecutionRequest(
                request.symbol(),
                request.quantity(),
                price,
                request.limitPrice() != null ? request.limitPrice() : price,
                request.orderType(),
                request.side(),
                request.candles(),
                request.atr(),
                bid,
                ask
        );
    }

    private ExecutionResult toResult(OrderIntent intent, ExecutionStatus status) {
        return new ExecutionResult(
                intent.getClientOrderId(),
                intent.getBrokerOrderId(),
                status,
                intent.getFilledQuantity() != null ? intent.getFilledQuantity() : 0,
                intent.getAveragePrice(),
                intent.getOrderState() == OrderState.REJECTED || intent.getOrderState() == OrderState.EXPIRED 
                    ? intent.getOrderState().name() : null
        );
    }

    private void sleep() {
        asyncDelayService.awaitMillis(pollDelayMs);
    }

    public record ExecutionRequestPayload(
            Long userId,
            String symbol,
            int quantity,
            ExecutionCostModel.OrderType orderType,
            ExecutionSide side,
            Double limitPrice,
            boolean paper,
            String clientOrderId,
            Double atr,
            List<Candle> candles,
            double referencePrice,
            Double stopLoss,
            boolean exitOrder,
            Long tradeId,
            Long signalId  // Link to StockScreeningResult
    ) {
        public ExecutionRequestPayload(Long userId, String symbol, int quantity,
                ExecutionCostModel.OrderType orderType, ExecutionSide side,
                Double limitPrice, boolean paper, String clientOrderId,
                Double atr, List<Candle> candles, double referencePrice,
                Double stopLoss, boolean exitOrder) {
            this(userId, symbol, quantity, orderType, side, limitPrice, paper,
                clientOrderId, atr, candles, referencePrice, stopLoss, exitOrder, null, null);
        }
    }

    public record ExecutionResult(
            String clientOrderId,
            String brokerOrderId,
            ExecutionStatus status,
            int filledQuantity,
            BigDecimal averagePrice,
            String rejectionReason
    ) {}

    public enum ExecutionStatus {
        PENDING,
        PARTIALLY_FILLED,
        FILLED,
        REJECTED,
        CANCELLED,
        EXPIRED,
        UNKNOWN;

        static ExecutionStatus from(String status) {
            if (status == null) {
                return UNKNOWN;
            }
            return switch (status.toUpperCase()) {
                case "FILLED", "COMPLETE" -> FILLED;
                case "REJECTED" -> REJECTED;
                case "CANCELLED", "CANCELED" -> CANCELLED;
                case "PARTIAL", "PARTIALLY_FILLED" -> PARTIALLY_FILLED;
                default -> PENDING;
            };
        }

        boolean isTerminal() {
            return this == FILLED || this == REJECTED || this == CANCELLED|| this == EXPIRED;
        }
    }
}
