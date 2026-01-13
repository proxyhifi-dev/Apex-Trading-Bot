package com.apex.backend.service;

import com.apex.backend.model.OrderIntent;
import com.apex.backend.model.OrderState;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.OrderIntentRepository;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reconciliation service to detect and repair state mismatches between
 * database and FYERS broker
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {
    private final FyersService fyersService;
    private final FyersAuthService fyersAuthService;
    private final OrderIntentRepository orderIntentRepository;
    private final TradeRepository tradeRepository;
    private final BroadcastService broadcastService;
    private final AlertService alertService;
    private final CircuitBreakerService circuitBreakerService;

    @Value("${reconcile.enabled:true}")
    private boolean enabled;

    @Value("${reconcile.interval-seconds:300}")
    private int intervalSeconds;

    @Value("${reconcile.safe-mode-on-mismatch:true}")
    private boolean safeModeOnMismatch;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (enabled) {
            log.info("Starting reconciliation on application ready");
            try {
                reconcile();
            } catch (Exception e) {
                log.error("Reconciliation failed on startup", e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${reconcile.interval-seconds}000")
    public void periodicReconcile() {
        if (enabled) {
            try {
                reconcile();
            } catch (Exception e) {
                log.error("Periodic reconciliation failed", e);
            }
        }
    }

    @Transactional
    public ReconciliationResult reconcile() {
        log.info("Starting reconciliation...");
        ReconciliationResult result = new ReconciliationResult(
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            false
        );

        // Get all users with active FYERS connections
        // For now, reconcile for all users (can be optimized later)
        List<Long> userIds = getActiveUserIds();
        
        for (Long userId : userIds) {
            try {
                String token = fyersAuthService.getFyersToken(userId);
                if (token == null || token.isBlank()) {
                    continue;
                }
                
                reconcileForUser(userId, token, result);
            } catch (Exception e) {
                log.error("Reconciliation failed for user: {}", userId, e);
            }
        }

        if (result.hasMismatches() && safeModeOnMismatch) {
            enterSafeMode(result);
        }

        if (result.hasMismatches()) {
            log.warn("Reconciliation found mismatches: {}", result.getSummary());
            broadcastReconciliationResult(result);
        } else {
            log.info("Reconciliation completed - no mismatches found");
        }

        return result;
    }

    private void reconcileForUser(Long userId, String token, ReconciliationResult result) {
        try {
            // Fetch open orders from FYERS
            Map<String, Object> brokerOrdersResponse = fyersService.getOrdersForUser(userId);
            List<FyersOrder> brokerOrders = parseBrokerOrders(brokerOrdersResponse);

            // Fetch expected orders from DB
            List<OrderIntent> dbOrders = orderIntentRepository.findByUserIdAndOrderStateIn(
                userId,
                List.of(OrderState.SENT, OrderState.ACKED, OrderState.PART_FILLED, OrderState.CANCEL_REQUESTED)
            );

            // Detect mismatches
            detectGhostOrders(brokerOrders, dbOrders, result);
            detectZombieOrders(brokerOrders, dbOrders, result);
            detectOrphanPositions(userId, token, result);
        } catch (Exception e) {
            log.error("Reconciliation failed for user: {}", userId, e);
        }
    }

    private void detectGhostOrders(List<FyersOrder> brokerOrders, List<OrderIntent> dbOrders, ReconciliationResult result) {
        Set<String> dbBrokerOrderIds = dbOrders.stream()
            .map(OrderIntent::getBrokerOrderId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        for (FyersOrder brokerOrder : brokerOrders) {
            if (!dbBrokerOrderIds.contains(brokerOrder.id())) {
                result.ghostOrders().add(brokerOrder);
                log.warn("Ghost order detected: {} status: {}", brokerOrder.id(), brokerOrder.status());
                // Create OrderIntent for ghost order
                createOrderIntentForGhost(brokerOrder);
            }
        }
    }

    private void detectZombieOrders(List<FyersOrder> brokerOrders, List<OrderIntent> dbOrders, ReconciliationResult result) {
        Set<String> brokerOrderIds = brokerOrders.stream()
            .map(FyersOrder::id)
            .collect(Collectors.toSet());

        for (OrderIntent dbOrder : dbOrders) {
            if (dbOrder.getBrokerOrderId() != null && !brokerOrderIds.contains(dbOrder.getBrokerOrderId())) {
                result.zombieOrders().add(dbOrder);
                log.warn("Zombie order detected: {} brokerOrderId: {} state: {}", 
                    dbOrder.getClientOrderId(), dbOrder.getBrokerOrderId(), dbOrder.getOrderState());
                // Mark as UNKNOWN or attempt cancel
                if (dbOrder.getOrderState() != OrderState.UNKNOWN) {
                    dbOrder.transitionTo(OrderState.UNKNOWN);
                    dbOrder.setLastBrokerStatus("NOT_FOUND_IN_BROKER");
                    orderIntentRepository.save(dbOrder);
                }
            }
        }
    }

    private void detectOrphanPositions(Long userId, String token, ReconciliationResult result) {
        try {
            // Fetch positions from FYERS
            Map<String, Object> brokerPositionsResponse = fyersService.getPositionsForUser(userId);
            List<FyersPosition> brokerPositions = parseBrokerPositions(brokerPositionsResponse);

            // Fetch open trades from DB
            List<Trade> dbTrades = tradeRepository.findByUserIdAndStatus(userId, Trade.TradeStatus.OPEN);

            // Check for orphan positions (broker has, DB doesn't)
            Set<String> dbSymbols = dbTrades.stream()
                .map(Trade::getSymbol)
                .collect(Collectors.toSet());

            for (FyersPosition brokerPosition : brokerPositions) {
                if (!dbSymbols.contains(brokerPosition.symbol())) {
                    result.orphanPositions().add(brokerPosition);
                    log.warn("Orphan position detected: {} qty: {}", brokerPosition.symbol(), brokerPosition.quantity());
                }
            }
        } catch (Exception e) {
            log.error("Failed to detect orphan positions for user: {}", userId, e);
        }
    }

    private void createOrderIntentForGhost(FyersOrder brokerOrder) {
        try {
            // Create a minimal OrderIntent to track the ghost order
            OrderIntent ghostIntent = OrderIntent.builder()
                .clientOrderId("GHOST-" + brokerOrder.id())
                .brokerOrderId(brokerOrder.id())
                .orderState(OrderState.UNKNOWN)
                .status("UNKNOWN")
                .lastBrokerStatus(brokerOrder.status())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
            orderIntentRepository.save(ghostIntent);
            log.info("Created OrderIntent for ghost order: {}", brokerOrder.id());
        } catch (Exception e) {
            log.error("Failed to create OrderIntent for ghost order: {}", brokerOrder.id(), e);
        }
    }

    private void enterSafeMode(ReconciliationResult result) {
        log.error("Entering safe mode due to reconciliation mismatches: {}", result.getSummary());
        alertService.sendAlert("RECONCILIATION_MISMATCH", result.getSummary());
        circuitBreakerService.triggerGlobalHalt("RECONCILIATION_MISMATCH: " + result.getSummary());
        
        Map<String, Object> statusEvent = new HashMap<>();
        statusEvent.put("status", "HALTED");
        statusEvent.put("reason", "RECONCILIATION_MISMATCH");
        statusEvent.put("details", result.getSummary());
        statusEvent.put("timestamp", LocalDateTime.now());
        broadcastService.broadcastBotStatus(statusEvent);
    }

    private void broadcastReconciliationResult(ReconciliationResult result) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "RECONCILIATION_MISMATCH");
        event.put("ghostOrders", result.ghostOrders().size());
        event.put("zombieOrders", result.zombieOrders().size());
        event.put("orphanPositions", result.orphanPositions().size());
        event.put("summary", result.getSummary());
        event.put("timestamp", LocalDateTime.now());
        broadcastService.broadcastBotStatus(event);
    }

    private List<Long> getActiveUserIds() {
        // TODO: Get from UserRepository where fyersConnected = true
        // For now, return empty list (will be implemented when needed)
        return new ArrayList<>();
    }

    private List<FyersOrder> parseBrokerOrders(Map<String, Object> response) {
        // TODO: Parse FYERS orders response
        // This is a placeholder - actual parsing depends on FYERS API response format
        return new ArrayList<>();
    }

    private List<FyersPosition> parseBrokerPositions(Map<String, Object> response) {
        // TODO: Parse FYERS positions response
        return new ArrayList<>();
    }

    public record ReconciliationResult(
        List<FyersOrder> ghostOrders,
        List<OrderIntent> zombieOrders,
        List<FyersPosition> orphanPositions,
        boolean hasMismatches
    ) {
        public String getSummary() {
            return String.format("Ghost: %d, Zombie: %d, Orphan: %d",
                ghostOrders.size(), zombieOrders.size(), orphanPositions.size());
        }
    }

    public record FyersOrder(String id, String status, String symbol, Integer quantity) {}
    public record FyersPosition(String symbol, Integer quantity, Double averagePrice) {}
}
