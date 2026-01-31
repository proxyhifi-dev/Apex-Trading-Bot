package com.apex.backend.service;

import com.apex.backend.model.PositionState;
import com.apex.backend.model.Trade;
import com.apex.backend.model.TradeStateAudit;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.repository.TradeStateAuditRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TradeStateMachine {

    private final TradeRepository tradeRepository;
    private final TradeStateAuditRepository tradeStateAuditRepository;
    private final AuditEventService auditEventService;

    @Transactional
    public boolean transition(Trade trade, PositionState target, String reason, String detail) {
        if (trade == null || target == null) {
            return false;
        }
        PositionState current = trade.getPositionState();
        if (current == target) {
            return false;
        }
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException("Invalid trade state transition: " + current + " -> " + target);
        }
        trade.transitionTo(target);
        tradeRepository.save(trade);
        TradeStateAudit audit = TradeStateAudit.builder()
                .tradeId(trade.getId())
                .userId(trade.getUserId())
                .fromState(current)
                .toState(target)
                .reason(reason)
                .detail(detail)
                .correlationId(MDC.get("correlationId"))
                .createdAt(Instant.now())
                .build();
        tradeStateAuditRepository.save(audit);
        auditEventService.recordEvent(trade.getUserId(), "trade_state_changed", "TRANSITION",
                "Trade state transition",
                Map.of("tradeId", trade.getId(), "from", current.name(), "to", target.name(), "reason", reason));
        return true;
    }
}
