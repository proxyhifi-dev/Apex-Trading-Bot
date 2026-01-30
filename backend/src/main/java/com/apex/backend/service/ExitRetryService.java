package com.apex.backend.service;

import com.apex.backend.model.ExitRetryRequest;
import com.apex.backend.model.PositionState;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.ExitRetryRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.service.ExecutionCostModel;
import com.apex.backend.service.ExecutionCostModel.ExecutionSide;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExitRetryService {

    private final ExitRetryRepository exitRetryRepository;
    private final TradeRepository tradeRepository;
    private final ExecutionEngine executionEngine;
    private final TradeCloseService tradeCloseService;
    private final DeadLetterQueueService deadLetterQueueService;

    @Value("${exit-retry.max-attempts:10}")
    private int maxAttempts;

    @Value("${exit-retry.retry-delay-seconds:30}")
    private int retryDelaySeconds;

    @Scheduled(fixedDelayString = "${exit-retry.poll-interval-ms:15000}")
    public void processQueue() {
        Instant now = Instant.now();
        List<ExitRetryRequest> pending = exitRetryRepository.findByResolvedFalseAndNextAttemptAtBefore(now);
        for (ExitRetryRequest request : pending) {
            attemptExit(request);
        }
    }

    @Transactional
    public void enqueueExit(Trade trade, String reason) {
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
    }

    @Transactional
    public void enqueueExitAndAttempt(Trade trade, String reason) {
        enqueueExit(trade, reason);
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
                    true
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
        request.setNextAttemptAt(Instant.now().plusSeconds(retryDelaySeconds));
        request.setUpdatedAt(Instant.now());
        if (request.getAttempts() >= maxAttempts && !request.isDlqLogged()) {
            deadLetterQueueService.logFailure("EXIT_RETRY", "tradeId=" + request.getTradeId(), error);
            request.setDlqLogged(true);
        }
        exitRetryRepository.save(request);
        log.warn("Exit retry scheduled tradeId={} attempts={} error={}", request.getTradeId(), request.getAttempts(), error);
    }
}
