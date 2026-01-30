package com.apex.backend.service;

import com.apex.backend.entity.SystemGuardState;
import com.apex.backend.model.OrderIntent;
import com.apex.backend.model.OrderState;
import com.apex.backend.model.Trade;
import com.apex.backend.model.User;
import com.apex.backend.repository.OrderIntentRepository;
import com.apex.backend.repository.PaperOrderRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.service.risk.BrokerPort;
import com.apex.backend.service.risk.FyersBrokerPort;
import com.apex.backend.service.risk.PaperBrokerPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmergencyPanicService {

    private final SystemGuardService systemGuardService;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final FyersBrokerPort fyersBrokerPort;
    private final PaperBrokerPort paperBrokerPort;
    private final OrderIntentRepository orderIntentRepository;
    private final PaperOrderRepository paperOrderRepository;
    private final TradeRepository tradeRepository;
    private final ExitRetryService exitRetryService;
    private final RiskEventService riskEventService;

    @Transactional
    public SystemGuardState triggerGlobalEmergency(String reason) {
        SystemGuardState state = systemGuardService.setEmergencyMode(true, reason, Instant.now());
        List<User> users = userRepository.findAll();
        for (User user : users) {
            Long userId = user.getId();
            boolean paperMode = settingsService.isPaperModeForUser(userId);
            BrokerPort brokerPort = paperMode ? paperBrokerPort : fyersBrokerPort;
            cancelAllOpenOrders(userId, brokerPort, paperMode);
            flattenAllPositions(userId);
        }
        revokeAllTokens();
        riskEventService.record(0L, "PANIC_TRIGGERED", reason, "users=" + users.size());
        log.warn("Global emergency panic triggered: reason={} users={}", reason, users.size());
        return state;
    }

    private void cancelAllOpenOrders(Long userId, BrokerPort brokerPort, boolean paperMode) {
        try {
            List<BrokerPort.BrokerOrder> openOrders = brokerPort.openOrders(userId);
            for (BrokerPort.BrokerOrder order : openOrders) {
                try {
                    brokerPort.cancelOrder(userId, order.orderId());
                } catch (Exception ex) {
                    log.warn("Emergency cancel failed userId={} orderId={} reason={}", userId, order.orderId(), ex.getMessage());
                }
            }
            if (!paperMode) {
                List<OrderIntent> intents = orderIntentRepository.findByUserIdAndOrderStateIn(
                        userId,
                        List.of(OrderState.CREATED, OrderState.SENT, OrderState.ACKED, OrderState.PART_FILLED, OrderState.CANCEL_REQUESTED)
                );
                for (OrderIntent intent : intents) {
                    if (intent.getOrderState() != OrderState.CANCEL_REQUESTED) {
                        intent.transitionTo(OrderState.CANCEL_REQUESTED);
                        orderIntentRepository.save(intent);
                    }
                }
            } else {
                paperOrderRepository.findByUserId(userId).forEach(order -> {
                    order.setStatus("CANCELLED");
                    paperOrderRepository.save(order);
                });
            }
        } catch (Exception ex) {
            log.warn("Emergency order cancellation failed for userId={}: {}", userId, ex.getMessage());
        }
    }

    private void flattenAllPositions(Long userId) {
        List<Trade> openTrades = tradeRepository.findByUserIdAndStatus(userId, Trade.TradeStatus.OPEN);
        for (Trade trade : openTrades) {
            exitRetryService.enqueueExitAndAttempt(trade, "EMERGENCY_PANIC");
        }
    }

    private void revokeAllTokens() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            user.setFyersToken(null);
            user.setFyersRefreshToken(null);
            user.setFyersConnected(false);
        }
        userRepository.saveAll(users);
    }
}
