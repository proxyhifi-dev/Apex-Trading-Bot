package com.apex.backend.service;

import com.apex.backend.model.Candle;
import com.apex.backend.model.OrderIntent;
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

    @Value("${execution.poll.max-attempts:12}")
    private int maxPollAttempts;

    @Value("${execution.poll.delay-ms:1000}")
    private long pollDelayMs;

    @Transactional
    public ExecutionResult execute(ExecutionRequestPayload request) {
        String clientOrderId = request.clientOrderId() == null || request.clientOrderId().isBlank()
                ? UUID.randomUUID().toString()
                : request.clientOrderId();
        org.slf4j.MDC.put("orderId", clientOrderId);
        try {
            Optional<OrderIntent> existing = orderIntentRepository.findByClientOrderId(clientOrderId);
            if (existing.isPresent()) {
                return toResult(existing.get(), ExecutionStatus.from(existing.get().getStatus()));
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
                orderIntentRepository.save(OrderIntent.builder()
                        .clientOrderId(clientOrderId)
                        .userId(request.userId())
                        .symbol(request.symbol())
                        .side(request.side().name())
                        .quantity(request.quantity())
                        .status("REJECTED")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build());
                String reasonCode = riskDecision.reason() != null ? riskDecision.reason().name() : "RISK_REJECTED";
                metricsService.recordReject(reasonCode);
                return new ExecutionResult(clientOrderId, null, ExecutionStatus.REJECTED, 0, null, reasonCode);
            }

            OrderIntent intent = orderIntentRepository.save(OrderIntent.builder()
                    .clientOrderId(clientOrderId)
                    .userId(request.userId())
                    .symbol(request.symbol())
                    .side(request.side().name())
                    .quantity(request.quantity())
                    .status("PENDING")
                    .createdAt(LocalDateTime.now())
                    .build());

            String token = request.paper() ? null : fyersAuthService.getFyersToken(request.userId());
            FyersQuote quote = marketDataClient.getQuote(request.symbol(), token).orElse(null);
            ExecutionCostModel.ExecutionRequest costRequest = buildCostRequest(request, quote, request.referencePrice());
            executionCostModel.estimateCost(clientOrderId, costRequest);

            if (request.paper()) {
                ExecutionCostModel.ExecutionEstimate estimate = executionCostModel.estimateExecution(costRequest);
                intent.setStatus("FILLED");
                intent.setFilledQuantity(request.quantity());
                intent.setAveragePrice(MoneyUtils.bd(estimate.effectivePrice()));
                intent.setUpdatedAt(LocalDateTime.now());
                orderIntentRepository.save(intent);
                ExecutionCostModel.ExecutionRequest realizedRequest = buildCostRequest(request, quote, estimate.effectivePrice());
                double realizedCost = executionCostModel.calculateAllInCost(realizedRequest);
                executionCostModel.updateRealizedCost(clientOrderId, realizedCost, "PAPER");
                metricsService.incrementOrdersPlaced();
                return toResult(intent, ExecutionStatus.FILLED);
            }

            if (token == null || token.isBlank()) {
                intent.setStatus("REJECTED");
                intent.setUpdatedAt(LocalDateTime.now());
                orderIntentRepository.save(intent);
                metricsService.recordReject("NO_BROKER_TOKEN");
                return new ExecutionResult(clientOrderId, null, ExecutionStatus.REJECTED, 0, null, "NO_BROKER_TOKEN");
            }

            String orderId = fyersService.placeOrder(
                    request.symbol(),
                    request.quantity(),
                    request.side().name(),
                    request.orderType().name(),
                    request.limitPrice() == null ? 0.0 : request.limitPrice(),
                    clientOrderId
            );
            intent.setBrokerOrderId(orderId);
            intent.setStatus("PLACED");
            intent.setUpdatedAt(LocalDateTime.now());
            orderIntentRepository.save(intent);

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
        }
    }

    private ExecutionResult pollUntilTerminal(OrderIntent intent, String token) {
        int attempts = 0;
        ExecutionResult lastResult = toResult(intent, ExecutionStatus.PENDING);
        while (attempts < maxPollAttempts) {
            attempts++;
            Optional<FyersOrderStatus> statusOpt = fyersService.getOrderDetails(intent.getBrokerOrderId(), token);
            if (statusOpt.isEmpty()) {
                sleep();
                continue;
            }
            FyersOrderStatus status = statusOpt.get();
            ExecutionStatus execStatus = ExecutionStatus.from(status.status());
            if (execStatus == ExecutionStatus.PARTIALLY_FILLED) {
                applyPartialFill(intent, status);
                lastResult = toResult(intent, execStatus);
                sleep();
                continue;
            }
            if (execStatus.isTerminal()) {
                if (execStatus == ExecutionStatus.FILLED) {
                    applyFinalFill(intent, status);
                } else {
                    intent.setStatus(execStatus.name());
                    intent.setUpdatedAt(LocalDateTime.now());
                    orderIntentRepository.save(intent);
                }
                return toResult(intent, execStatus);
            }
            lastResult = toResult(intent, execStatus);
            sleep();
        }
        return lastResult;
    }

    private void applyPartialFill(OrderIntent intent, FyersOrderStatus status) {
        if (status.filledQuantity() != null && status.filledQuantity() > 0) {
            intent.setFilledQuantity(status.filledQuantity());
        }
        if (status.averagePrice() != null) {
            intent.setAveragePrice(status.averagePrice());
        }
        intent.setStatus(ExecutionStatus.PARTIALLY_FILLED.name());
        intent.setUpdatedAt(LocalDateTime.now());
        orderIntentRepository.save(intent);
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
        intent.setStatus(ExecutionStatus.FILLED.name());
        intent.setUpdatedAt(LocalDateTime.now());
        orderIntentRepository.save(intent);
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
                null
        );
    }

    private void sleep() {
        try {
            Thread.sleep(pollDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
            boolean exitOrder
    ) {}

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
            return this == FILLED || this == REJECTED || this == CANCELLED;
        }
    }
}
