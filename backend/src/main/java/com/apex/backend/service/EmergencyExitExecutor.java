package com.apex.backend.service;

import com.apex.backend.event.EmergencyPanicRequestedEvent;
import com.apex.backend.model.ExitRetryRequest;
import com.apex.backend.model.PositionState;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.ExitRetryRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.service.ExecutionCostModel.ExecutionSide;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class EmergencyExitExecutor {

    private final ExitRetryRepository exitRetryRepository;
    private final TradeRepository tradeRepository;
    private final ExecutionEngine executionEngine;
    private final TradeCloseService tradeCloseService;
    private final DeadLetterQueueService deadLetterQueueService;
    private final AuditEventService auditEventService;
    private final AlertService alertService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${exit-retry.max-attempts:10}")
    private int maxAttempts;

    @Value("${exit-retry.retry-delay-seconds:30}")
    private int retryDelaySeconds;

    @Value("${exit-retry.max-delay-seconds:300}")
    private int maxDelaySeconds;

    @Transactional
    public void enqueueExitAndAttempt(Trade trade, String reason) {
        if (trade == null || trade.getStatus() == Trade.TradeStatus.CLOSED) {
            return;
        }
        if (!exitRetryRepository.findByTradeIdAndResolvedFalse(trade.getId()).isEmpty()) {
            return;
        }
        ExitRetryRequest request = ExitRetryRequest.builder()
                .tradeId(trade.getId())
                .userId(trade.getUserId())
                .symbol(trade.getSymbol())
                .quantity(trade.getQuantity())
                .side(trade.getTradeType() == Trade.TradeType.LONG ? "SELL" : "BUY")
                .paper(trade.isPaperTrade())
                .attempts(0)
                .resolved(false)
                .nextAttemptAt(Instant.now())
                .reason(reason)
                .dlqLogged(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        exitRetryRepository.save(request);
        tradeCloseService.markClosing(trade, reason);
        exitRetryRepository.findByTradeIdAndResolvedFalse(trade.getId())
                .forEach(this::attemptExit);
    }

    @Transactional
    public void attemptExit(ExitRetryRequest request) {
        Trade trade = tradeRepository.findById(request.getTradeId()).orElse(null);
        if (trade == null || trade.getStatus() == Trade.TradeStatus.CLOSED) {
            markResolved(request, "Trade already closed");
            return;
        }
        if (trade.getPositionState() != PositionState.CLOSING) {
            tradeCloseService.markClosing(trade, "EXIT_RETRY");
        }
        request.setAttempts(request.getAttempts() + 1);
        request.setLastAttemptAt(Instant.now());
        request.setUpdatedAt(Instant.now());

        try {
            ExecutionEngine.ExecutionResult result = executionEngine.execute(new ExecutionEngine.ExecutionRequestPayload(
                    trade.getUserId(),
                    trade.getSymbol(),
                    trade.getQuantity(),
                    ExecutionCostModel.OrderType.MARKET,
                    ExecutionSide.valueOf(request.getSide()),
                    null,
                    trade.isPaperTrade(),
                    "EXIT-RETRY-" + trade.getId() + "-" + request.getAttempts(),
                    trade.getAtr() != null ? trade.getAtr().doubleValue() : 0.0,
                    List.of(),
                    trade.getEntryPrice().doubleValue(),
                    trade.getCurrentStopLoss() != null ? trade.getCurrentStopLoss().doubleValue() : null,
                    true,
                    trade.getId(),
                    null
            ));
            if (result.status() == ExecutionEngine.ExecutionStatus.FILLED) {
                tradeCloseService.finalizeTrade(trade,
                        result.averagePrice() != null ? result.averagePrice() : trade.getEntryPrice(),
                        Trade.ExitReason.MANUAL,
                        "EXIT_RETRY_FILLED");
                markResolved(request, "Filled");
                return;
            }
            scheduleRetry(request, "Exit status=" + result.status());
        } catch (Exception ex) {
            scheduleRetry(request, ex.getMessage());
        }
    }

    private void markResolved(ExitRetryRequest request, String reason) {
        request.setResolved(true);
        request.setUpdatedAt(Instant.now());
        request.setLastError(reason);
        exitRetryRepository.save(request);
    }

    private void scheduleRetry(ExitRetryRequest request, String error) {
        request.setLastError(error);
        int attempt = Math.max(1, request.getAttempts());
        long delay = Math.min(maxDelaySeconds, (long) retryDelaySeconds * (1L << Math.min(attempt - 1, 8)));
        request.setNextAttemptAt(Instant.now().plusSeconds(delay));
        request.setUpdatedAt(Instant.now());
        if (request.getAttempts() >= maxAttempts && !request.isDlqLogged()) {
            deadLetterQueueService.logFailure("EXIT_RETRY", "tradeId=" + request.getTradeId(), error);
            request.setDlqLogged(true);
            auditEventService.recordEvent(request.getUserId(), "risk_event", "EXIT_RETRY_DLQ",
                    "Exit retry exhausted attempts",
                    Map.of("tradeId", request.getTradeId(), "attempts", request.getAttempts(), "error", error));
            eventPublisher.publishEvent(new EmergencyPanicRequestedEvent(
                    request.getUserId(),
                    "EXIT_RETRY_DLQ",
                    "EmergencyExitExecutor",
                    Instant.now(),
                    Map.of("tradeId", request.getTradeId(), "attempts", request.getAttempts(), "error", error)
            ));
            alertService.sendAlert("EXIT_RETRY_DLQ", "Exit retry dead-lettered tradeId=" + request.getTradeId());
        }
        exitRetryRepository.save(request);
        log.warn("Exit retry scheduled tradeId={} attempts={} delaySeconds={} error={}",
                request.getTradeId(), request.getAttempts(), delay, error);
    }
}
