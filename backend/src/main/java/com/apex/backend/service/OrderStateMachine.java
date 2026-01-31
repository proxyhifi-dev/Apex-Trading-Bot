package com.apex.backend.service;

import com.apex.backend.model.OrderIntent;
import com.apex.backend.model.OrderState;
import com.apex.backend.repository.OrderIntentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderStateMachine {

    private final OrderIntentRepository orderIntentRepository;
    private final AuditEventService auditEventService;

    @Transactional
    public boolean transition(OrderIntent intent, OrderState target, String reason) {
        if (intent == null || target == null) {
            return false;
        }
        OrderState current = intent.getOrderState();
        if (current == target) {
            return false;
        }
        intent.transitionTo(target);
        orderIntentRepository.save(intent);
        auditEventService.recordEvent(intent.getUserId(), "order_state_changed", "TRANSITION",
                "Order state transition",
                Map.of("orderId", intent.getClientOrderId(), "from", current.name(), "to", target.name(), "reason", reason));
        return true;
    }
}
