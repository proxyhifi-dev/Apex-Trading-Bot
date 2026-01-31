package com.apex.backend.service;

import com.apex.backend.model.PositionState;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class StopLossEnforcementService {

    private final TradeRepository tradeRepository;
    private final ExecutionEngine executionEngine;
    private final SystemGuardService systemGuardService;
    private final AlertService alertService;
    private final MetricsService metricsService;
    private final AuditEventService auditEventService;
    private final ExitRetryService exitRetryService;
    private final TradeStateMachine tradeStateMachine;
    private final EmergencyPanicService emergencyPanicService;

    @Value("${execution.stop-ack-timeout-seconds:5}")
    private int stopAckTimeoutSeconds;

    @Value("${apex.risk.stop-loss-failure-mode:SAFE}")
    private String stopLossFailureMode;

    @Scheduled(fixedDelayString = "${execution.stop-ack-enforce-interval-seconds:5}000")
    @Transactional
    public void enforceOverdueStops() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(stopAckTimeoutSeconds);
        List<Trade> overdue = tradeRepository.findByPositionStateAndStopAckedAtIsNullAndEntryTimeBefore(
                PositionState.OPENING, cutoff);
        for (Trade trade : overdue) {
            enforce(trade, "STOP_ACK_TIMEOUT");
        }
    }

    @Transactional
    public void enforce(Trade trade, String reason) {
        if (trade == null || trade.getId() == null) {
            return;
        }
        log.error("Stop-loss ACK enforcement triggered for trade {} symbol: {} reason: {}", trade.getId(), trade.getSymbol(), reason);
        auditEventService.recordEvent(trade.getUserId(), "STOP_LOSS", "ENFORCE",
                "Stop-loss ACK enforcement triggered",
                Map.of("tradeId", trade.getId(), "symbol", trade.getSymbol(), "reason", reason));
        metricsService.recordStopLossFailure();

        tradeStateMachine.transition(trade, PositionState.ERROR, "STOP_LOSS_FAIL", reason);

        flattenPosition(trade);

        if ("PANIC".equalsIgnoreCase(stopLossFailureMode)) {
            emergencyPanicService.triggerGlobalEmergency("STOP_LOSS_FAIL:" + trade.getSymbol());
        } else {
            systemGuardService.setSafeMode(true, "STOP_LOSS_ACK_TIMEOUT: " + trade.getSymbol(), Instant.now());
        }
        alertService.sendAlert("STOP_LOSS_ENFORCEMENT", "Stop-loss ack timeout for " + trade.getSymbol());
    }

    private void flattenPosition(Trade trade) {
        try {
            ExecutionEngine.ExecutionRequestPayload exitRequest = new ExecutionEngine.ExecutionRequestPayload(
                    trade.getUserId(),
                    trade.getSymbol(),
                    trade.getQuantity(),
                    ExecutionCostModel.OrderType.MARKET,
                    trade.getTradeType() == Trade.TradeType.LONG
                            ? ExecutionCostModel.ExecutionSide.SELL
                            : ExecutionCostModel.ExecutionSide.BUY,
                    null,
                    false,
                    "FLATTEN-" + trade.getId(),
                    trade.getAtr() != null ? trade.getAtr().doubleValue() : 0.0,
                    null,
                    trade.getEntryPrice().doubleValue(),
                    null,
                    true,
                    trade.getId(),
                    null
            );
            executionEngine.execute(exitRequest);
            metricsService.recordEmergencyFlatten();
        } catch (Exception e) {
            log.error("Failed to flatten position for trade {}: {}", trade.getId(), e.getMessage());
            exitRetryService.enqueueExitAndAttempt(trade, "STOP_LOSS_ENFORCE");
        }
    }
}
