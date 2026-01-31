package com.apex.backend.service.risk;

import com.apex.backend.entity.SystemGuardState;
import com.apex.backend.model.OrderIntent;
import com.apex.backend.model.OrderState;
import com.apex.backend.model.PaperOrder;
import com.apex.backend.model.PaperPosition;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.OrderIntentRepository;
import com.apex.backend.repository.PaperOrderRepository;
import com.apex.backend.repository.PaperPositionRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.service.BroadcastService;
import com.apex.backend.service.DecisionAuditService;
import com.apex.backend.service.SettingsService;
import com.apex.backend.service.SystemGuardService;
import com.apex.backend.service.AuditEventService;
import com.apex.backend.service.ExecutionCostModel;
import com.apex.backend.service.ExecutionEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReconciliationService {

    private final OrderIntentRepository orderIntentRepository;
    private final TradeRepository tradeRepository;
    private final PaperOrderRepository paperOrderRepository;
    private final PaperPositionRepository paperPositionRepository;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final SystemGuardService systemGuardService;
    private final BroadcastService broadcastService;
    private final DecisionAuditService decisionAuditService;
    private final FyersBrokerPort fyersBrokerPort;
    private final PaperBrokerPort paperBrokerPort;
    private final ExecutionEngine executionEngine;
    private final AuditEventService auditEventService;
    private final ExitRetryService exitRetryService;
    private final TradeStateMachine tradeStateMachine;
    private final EmergencyPanicService emergencyPanicService;
    private final OrderStateMachine orderStateMachine;

    private final java.util.concurrent.atomic.AtomicReference<ReconcileReport> lastReport =
            new java.util.concurrent.atomic.AtomicReference<>(new ReconcileReport(false, List.of(), List.of(), List.of()));

    @Value("${reconcile.enabled:true}")
    private boolean enabled;

    @Value("${reconcile.safe-mode-on-mismatch:true}")
    private boolean safeModeOnMismatch;

    @Value("${reconcile.qty-tolerance:1}")
    private int qtyTolerance;

    @Value("${reconcile.price-tolerance-pct:0.25}")
    private double priceTolerancePct;

    @Value("${reconcile.auto-cancel-pending-on-mismatch:true}")
    private boolean autoCancelPendingOnMismatch;

    @Value("${reconcile.auto-flatten-on-mismatch:false}")
    private boolean autoFlattenOnMismatch;

    @Value("${reconcile.panic-on-mismatch:false}")
    private boolean panicOnMismatch;

    @Scheduled(fixedDelayString = "${reconcile.interval-ms:60000}")
    public void runScheduled() {
        try {
            reconcile();
        } catch (Exception e) {
            log.error("Reconciliation task failed", e);
        }
    }

    @Transactional
    public ReconcileReport reconcile() {
        if (!enabled) {
            return new ReconcileReport(false, List.of(), List.of(), List.of());
        }
        Instant now = Instant.now();
        List<Long> userIds = userRepository.findAll().stream().map(user -> user.getId()).toList();
        List<String> orderMismatches = new ArrayList<>();
        List<String> positionMismatches = new ArrayList<>();
        List<String> statusMismatches = new ArrayList<>();

        for (Long userId : userIds) {
            boolean paperMode = settingsService.isPaperModeForUser(userId);
            BrokerPort brokerPort = paperMode ? paperBrokerPort : fyersBrokerPort;

            int orderMismatchStart = orderMismatches.size();
            int statusMismatchStart = statusMismatches.size();
            int positionMismatchStart = positionMismatches.size();

            List<BrokerPort.BrokerOrder> brokerOrders = brokerPort.openOrders(userId);
            List<OrderSnapshot> dbOrders = loadDbOrders(userId, paperMode);
            compareOrders(dbOrders, brokerOrders, orderMismatches, statusMismatches);

            List<BrokerPort.BrokerPosition> brokerPositions = brokerPort.openPositions(userId);
            List<PositionSnapshot> dbPositions = loadDbPositions(userId, paperMode);
            comparePositions(dbPositions, brokerPositions, positionMismatches);

            boolean mismatchForUser = orderMismatches.size() > orderMismatchStart
                    || statusMismatches.size() > statusMismatchStart
                    || positionMismatches.size() > positionMismatchStart;
            if (mismatchForUser) {
                handleMismatch(userId, brokerPort, dbOrders);
                cancelUnknownOrders(userId, brokerPort, dbOrders, brokerOrders);
                flattenUnknownPositions(userId, brokerPort, dbPositions, brokerPositions);
            }
        }

        boolean hasMismatch = !(orderMismatches.isEmpty() && statusMismatches.isEmpty() && positionMismatches.isEmpty());
        SystemGuardState state = systemGuardService.updateReconcileTimestamp(now);
        if (hasMismatch && safeModeOnMismatch) {
            String summary = summary(orderMismatches, statusMismatches, positionMismatches);
            state = systemGuardService.setSafeMode(true, summary, now);
            auditEventService.recordEvent(0L, "reconciliation_mismatch", "MISMATCH",
                    "Reconciliation mismatch detected", Map.of(
                            "orders", orderMismatches,
                            "statuses", statusMismatches,
                            "positions", positionMismatches));
            if (panicOnMismatch && !positionMismatches.isEmpty()) {
                emergencyPanicService.triggerGlobalEmergency("RECONCILE_POSITION_MISMATCH");
            }
        }
        decisionAuditService.record("SYSTEM", "N/A", "RECONCILE", Map.of(
                "mismatch", hasMismatch,
                "orders", orderMismatches.size(),
                "statuses", statusMismatches.size(),
                "positions", positionMismatches.size()
        ));
        broadcastReport(hasMismatch, orderMismatches, statusMismatches, positionMismatches, state);
        ReconcileReport report = new ReconcileReport(hasMismatch, orderMismatches, statusMismatches, positionMismatches);
        lastReport.set(report);
        return report;
    }

    public ReconcileReport getLastReport() {
        return lastReport.get();
    }

    @Transactional
    public ReconcileRepairReport repair(ReconcileRepairRequest request) {
        boolean cancelOrders = request.cancelStuckOrders();
        boolean quarantineTrades = request.quarantineTrades();
        boolean flatten = request.flattenPositions();
        List<Long> userIds = request.userId() != null
                ? List.of(request.userId())
                : userRepository.findAll().stream().map(user -> user.getId()).toList();

        int cancelRequested = 0;
        int cancelFailed = 0;
        int tradesQuarantined = 0;
        int tradesFlattened = 0;
        List<String> notes = new ArrayList<>();

        for (Long userId : userIds) {
            boolean paperMode = settingsService.isPaperModeForUser(userId);
            BrokerPort brokerPort = paperMode ? paperBrokerPort : fyersBrokerPort;

            if (cancelOrders) {
                List<OrderSnapshot> dbOrders = loadDbOrders(userId, paperMode);
                for (OrderSnapshot dbOrder : dbOrders) {
                    if (!dbOrder.open()) {
                        continue;
                    }
                    if (dbOrder.orderId() == null || dbOrder.orderId().isBlank()) {
                        continue;
                    }
                    try {
                        brokerPort.cancelOrder(userId, dbOrder.orderId());
                        OrderIntent intent = dbOrder.markCancelRequested();
                        if (intent != null) {
                            orderStateMachine.transition(intent, OrderState.CANCEL_REQUESTED, "RECONCILE_REPAIR");
                        }
                        cancelRequested++;
                    } catch (Exception e) {
                        cancelFailed++;
                        notes.add("Cancel failed user=" + userId + " order=" + dbOrder.orderId() + " reason=" + e.getMessage());
                    }
                }
            }

            if (quarantineTrades) {
                List<Trade> trades = tradeRepository.findByUserIdAndStatus(userId, Trade.TradeStatus.OPEN);
                for (Trade trade : trades) {
                    if (trade.getPositionState() != com.apex.backend.model.PositionState.ERROR) {
                        trade.setExitReasonDetail("RECONCILE_QUARANTINE");
                        tradeStateMachine.transition(trade, com.apex.backend.model.PositionState.ERROR, "RECONCILE_QUARANTINE", "Manual quarantine");
                        tradesQuarantined++;
                    }
                }
            }

            if (flatten) {
                List<Trade> trades = tradeRepository.findByUserIdAndStatus(userId, Trade.TradeStatus.OPEN);
                for (Trade trade : trades) {
                    try {
                        executionEngine.execute(new ExecutionEngine.ExecutionRequestPayload(
                                trade.getUserId(),
                                trade.getSymbol(),
                                trade.getQuantity(),
                                ExecutionCostModel.OrderType.MARKET,
                                trade.getTradeType() == Trade.TradeType.LONG
                                        ? ExecutionCostModel.ExecutionSide.SELL
                                        : ExecutionCostModel.ExecutionSide.BUY,
                                null,
                                trade.isPaperTrade(),
                                "RECON-FLATTEN-" + trade.getId(),
                                trade.getAtr() != null ? trade.getAtr().doubleValue() : 0.0,
                                List.of(),
                                trade.getEntryPrice().doubleValue(),
                                null,
                                true,
                                trade.getId(),
                                null
                        ));
                        tradesFlattened++;
                    } catch (Exception e) {
                        notes.add("Flatten failed user=" + userId + " trade=" + trade.getId() + " reason=" + e.getMessage());
                    }
                }
            }
        }

        ReconcileRepairReport report = new ReconcileRepairReport(true, userIds.size(), cancelRequested, cancelFailed,
                tradesQuarantined, tradesFlattened, LocalDateTime.now(), notes);
        auditEventService.recordEvent(null, "RECONCILE", "REPAIR",
                "Reconciliation repair executed",
                Map.of(
                        "usersProcessed", userIds.size(),
                        "cancelRequested", cancelRequested,
                        "cancelFailed", cancelFailed,
                        "tradesQuarantined", tradesQuarantined,
                        "tradesFlattened", tradesFlattened,
                        "flatten", flatten,
                        "quarantineTrades", quarantineTrades,
                        "cancelStuckOrders", cancelOrders
                ));
        return report;
    }

    public record ReconcileRepairRequest(Long userId, boolean cancelStuckOrders, boolean quarantineTrades, boolean flattenPositions) {}
    public record ReconcileRepairReport(boolean success, int usersProcessed, int cancelRequested, int cancelFailed,
                                        int tradesQuarantined, int tradesFlattened, LocalDateTime timestamp,
                                        List<String> notes) {}

    private void compareOrders(List<OrderSnapshot> dbOrders, List<BrokerPort.BrokerOrder> brokerOrders,
                               List<String> orderMismatches, List<String> statusMismatches) {
        Map<String, BrokerPort.BrokerOrder> brokerById = brokerOrders.stream()
                .filter(order -> order.orderId() != null)
                .collect(Collectors.toMap(BrokerPort.BrokerOrder::orderId, order -> order, (a, b) -> a));
        Set<String> dbIds = dbOrders.stream().map(OrderSnapshot::orderId).filter(Objects::nonNull).collect(Collectors.toSet());

        for (OrderSnapshot dbOrder : dbOrders) {
            BrokerPort.BrokerOrder brokerOrder = brokerById.get(dbOrder.orderId());
            if (brokerOrder == null) {
                orderMismatches.add("DB open order missing in broker: " + dbOrder.orderId());
                continue;
            }
            if (isTerminalStatus(brokerOrder.status()) && dbOrder.open()) {
                statusMismatches.add("Order status mismatch for " + dbOrder.orderId() + " broker=" + brokerOrder.status());
            }
            int filledQty = brokerOrder.filledQty() != null ? brokerOrder.filledQty() : 0;
            if (Math.abs(filledQty - dbOrder.filledQty()) > qtyTolerance) {
                statusMismatches.add("Order filledQty mismatch for " + dbOrder.orderId());
            }
            if (brokerOrder.averagePrice() != null && dbOrder.averagePrice() != null) {
                double diffPct = Math.abs(brokerOrder.averagePrice().doubleValue() - dbOrder.averagePrice().doubleValue())
                        / Math.max(1.0, dbOrder.averagePrice().doubleValue()) * 100.0;
                if (diffPct > priceTolerancePct) {
                    statusMismatches.add("Order price mismatch for " + dbOrder.orderId());
                }
            }
        }
        for (BrokerPort.BrokerOrder brokerOrder : brokerOrders) {
            if (!dbIds.contains(brokerOrder.orderId())) {
                orderMismatches.add("Broker open order missing in DB: " + brokerOrder.orderId());
            }
        }
    }

    private void comparePositions(List<PositionSnapshot> dbPositions, List<BrokerPort.BrokerPosition> brokerPositions,
                                  List<String> positionMismatches) {
        Map<String, BrokerPort.BrokerPosition> brokerBySymbol = brokerPositions.stream()
                .filter(position -> position.symbol() != null)
                .collect(Collectors.toMap(BrokerPort.BrokerPosition::symbol, position -> position, (a, b) -> a));
        Set<String> dbSymbols = dbPositions.stream().map(PositionSnapshot::symbol).collect(Collectors.toSet());

        for (PositionSnapshot dbPosition : dbPositions) {
            BrokerPort.BrokerPosition brokerPosition = brokerBySymbol.get(dbPosition.symbol());
            if (brokerPosition == null) {
                positionMismatches.add("DB position missing in broker: " + dbPosition.symbol());
                continue;
            }
            int brokerQty = brokerPosition.netQty() != null ? brokerPosition.netQty() : 0;
            if (Math.abs(brokerQty - dbPosition.netQty()) > qtyTolerance) {
                positionMismatches.add("Position qty mismatch for " + dbPosition.symbol());
            }
        }
        for (BrokerPort.BrokerPosition brokerPosition : brokerPositions) {
            if (!dbSymbols.contains(brokerPosition.symbol())) {
                positionMismatches.add("Broker position missing in DB: " + brokerPosition.symbol());
            }
        }
    }

    void handleMismatch(Long userId, BrokerPort brokerPort, List<OrderSnapshot> dbOrders) {
        int attempted = 0;
        int skipped = 0;
        int failed = 0;
        for (OrderSnapshot dbOrder : dbOrders) {
            if (dbOrder.open()) {
                if (dbOrder.orderId() == null || dbOrder.orderId().isBlank()) {
                    skipped++;
                    continue;
                }
                attempted++;
                try {
                    brokerPort.cancelOrder(userId, dbOrder.orderId());
                    OrderIntent intent = dbOrder.markCancelRequested();
                    if (intent != null) {
                        orderStateMachine.transition(intent, OrderState.CANCEL_REQUESTED, "RECONCILE_MISMATCH");
                    }
                    log.debug("Cancel requested for orderId={}", dbOrder.orderId());
                } catch (Exception ex) {
                    failed++;
                    log.debug("Cancel failed for orderId={}: {}", dbOrder.orderId(), ex.getMessage());
                }
            }
        }
        if (attempted > 0 || skipped > 0 || failed > 0) {
            log.info("Reconcile cancel summary: attempted={}, skipped={}, failed={}", attempted, skipped, failed);
        }
        if (autoFlattenOnMismatch) {
            List<Trade> trades = tradeRepository.findByUserIdAndStatus(userId, Trade.TradeStatus.OPEN);
            for (Trade trade : trades) {
                tradeStateMachine.transition(trade, com.apex.backend.model.PositionState.ERROR, "RECONCILE_MISMATCH", "Auto-flatten");
                exitRetryService.enqueueExitAndAttempt(trade, "RECONCILE_MISMATCH");
            }
        }
    }

    private void cancelUnknownOrders(Long userId, BrokerPort brokerPort, List<OrderSnapshot> dbOrders,
                                     List<BrokerPort.BrokerOrder> brokerOrders) {
        Set<String> dbIds = dbOrders.stream()
                .map(OrderSnapshot::orderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (BrokerPort.BrokerOrder brokerOrder : brokerOrders) {
            if (brokerOrder.orderId() == null || dbIds.contains(brokerOrder.orderId())) {
                continue;
            }
            try {
                brokerPort.cancelOrder(userId, brokerOrder.orderId());
                log.warn("Cancelled unknown broker order {} for user {}", brokerOrder.orderId(), userId);
            } catch (Exception ex) {
                log.warn("Failed to cancel unknown broker order {} for user {}: {}", brokerOrder.orderId(), userId, ex.getMessage());
            }
        }
    }

    private void flattenUnknownPositions(Long userId, BrokerPort brokerPort, List<PositionSnapshot> dbPositions,
                                         List<BrokerPort.BrokerPosition> brokerPositions) {
        Set<String> dbSymbols = dbPositions.stream().map(PositionSnapshot::symbol).collect(Collectors.toSet());
        for (BrokerPort.BrokerPosition brokerPosition : brokerPositions) {
            if (brokerPosition.symbol() == null || dbSymbols.contains(brokerPosition.symbol())) {
                continue;
            }
            try {
                executionEngine.execute(new ExecutionEngine.ExecutionRequestPayload(
                        userId,
                        brokerPosition.symbol(),
                        Math.abs(brokerPosition.netQty()),
                        ExecutionCostModel.OrderType.MARKET,
                        brokerPosition.netQty() > 0
                                ? ExecutionCostModel.ExecutionSide.SELL
                                : ExecutionCostModel.ExecutionSide.BUY,
                        null,
                        settingsService.isPaperModeForUser(userId),
                        "RECON-UNKNOWN-FLAT-" + brokerPosition.symbol(),
                        0.0,
                        List.of(),
                        brokerPosition.avgPrice() != null ? brokerPosition.avgPrice().doubleValue() : 0.0,
                        null,
                        true,
                        null,
                        null
                ));
                log.warn("Flattened unknown broker position {} for user {}", brokerPosition.symbol(), userId);
            } catch (Exception ex) {
                log.warn("Failed to flatten unknown broker position {} for user {}: {}", brokerPosition.symbol(), userId, ex.getMessage());
            }
        }
    }

    private List<OrderSnapshot> loadDbOrders(Long userId, boolean paperMode) {
        if (paperMode) {
            return paperOrderRepository.findByUserId(userId).stream()
                    .filter(order -> isOpen(order.getStatus()))
                    .map(order -> new OrderSnapshot(order.getOrderId(), order.getSymbol(), order.getStatus(),
                            order.getQuantity() != null ? order.getQuantity() : 0, order.getPrice(), true))
                    .toList();
        }
        List<OrderIntent> intents = orderIntentRepository.findByUserIdAndOrderStateIn(userId,
                List.of(OrderState.CREATED, OrderState.SENT, OrderState.ACKED, OrderState.PART_FILLED, OrderState.CANCEL_REQUESTED));
        return intents.stream()
                .map(intent -> new OrderSnapshot(resolveOrderId(intent), intent.getSymbol(), intent.getOrderState().name(),
                        intent.getFilledQuantity() != null ? intent.getFilledQuantity() : 0, intent.getAveragePrice(), true, intent))
                .toList();
    }

    private List<PositionSnapshot> loadDbPositions(Long userId, boolean paperMode) {
        if (paperMode) {
            return paperPositionRepository.findByUserIdAndStatus(userId, "OPEN").stream()
                    .map(position -> new PositionSnapshot(position.getSymbol(), position.getQuantity() != null ? position.getQuantity() : 0))
                    .toList();
        }
        return tradeRepository.findByUserIdAndStatus(userId, Trade.TradeStatus.OPEN).stream()
                .map(trade -> new PositionSnapshot(trade.getSymbol(), trade.getQuantity() != null ? trade.getQuantity() : 0))
                .toList();
    }

    private void broadcastReport(boolean hasMismatch, List<String> orderMismatches, List<String> statusMismatches,
                                 List<String> positionMismatches, SystemGuardState state) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("mismatch", hasMismatch);
        payload.put("orderMismatches", orderMismatches);
        payload.put("statusMismatches", statusMismatches);
        payload.put("positionMismatches", positionMismatches);
        payload.put("safeMode", state.isSafeMode());
        payload.put("lastMismatchReason", state.getLastMismatchReason());
        payload.put("timestamp", Instant.now().toString());
        broadcastService.broadcastBotStatus(payload);
        broadcastService.broadcastReconcile(payload);
    }

    private String resolveOrderId(OrderIntent intent) {
        return intent.getBrokerOrderId() != null ? intent.getBrokerOrderId() : intent.getClientOrderId();
    }

    private boolean isOpen(String status) {
        if (status == null) {
            return true;
        }
        String normalized = status.toUpperCase();
        return !("FILLED".equals(normalized) || "CANCELLED".equals(normalized) || "REJECTED".equals(normalized) || "EXPIRED".equals(normalized));
    }

    private boolean isTerminalStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.toUpperCase();
        return normalized.contains("FILLED") || normalized.contains("CANCEL") || normalized.contains("REJECT") || normalized.contains("EXPIRED");
    }

    private String summary(List<String> orderMismatches, List<String> statusMismatches, List<String> positionMismatches) {
        return "orders=" + orderMismatches.size() + ", status=" + statusMismatches.size() + ", positions=" + positionMismatches.size();
    }

    public record ReconcileReport(boolean mismatch, List<String> orderMismatches, List<String> statusMismatches, List<String> positionMismatches) {}

    static class OrderSnapshot {
        private final String orderId;
        private final String symbol;
        private final String status;
        private final int filledQty;
        private final BigDecimal averagePrice;
        private final boolean open;
        private final OrderIntent intent;

        OrderSnapshot(String orderId, String symbol, String status, int filledQty, BigDecimal averagePrice, boolean open) {
            this(orderId, symbol, status, filledQty, averagePrice, open, null);
        }

        OrderSnapshot(String orderId, String symbol, String status, int filledQty, BigDecimal averagePrice, boolean open, OrderIntent intent) {
            this.orderId = orderId;
            this.symbol = symbol;
            this.status = status;
            this.filledQty = filledQty;
            this.averagePrice = averagePrice;
            this.open = open;
            this.intent = intent;
        }

        public String orderId() {
            return orderId;
        }

        public int filledQty() {
            return filledQty;
        }

        public BigDecimal averagePrice() {
            return averagePrice;
        }

        public boolean open() {
            return open;
        }

        public OrderIntent markCancelRequested() {
            return intent;
        }
    }

    private record PositionSnapshot(String symbol, int netQty) {}
}
