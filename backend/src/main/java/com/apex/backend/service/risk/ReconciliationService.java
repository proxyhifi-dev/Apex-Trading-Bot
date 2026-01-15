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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
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

    @Scheduled(fixedDelayString = "${reconcile.interval-seconds:300}000")
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
            }
        }

        boolean hasMismatch = !(orderMismatches.isEmpty() && statusMismatches.isEmpty() && positionMismatches.isEmpty());
        SystemGuardState state = systemGuardService.updateReconcileTimestamp(now);
        if (hasMismatch && safeModeOnMismatch) {
            String summary = summary(orderMismatches, statusMismatches, positionMismatches);
            state = systemGuardService.setSafeMode(true, summary, now);
        }
        decisionAuditService.record("SYSTEM", "N/A", "RECONCILE", Map.of(
                "mismatch", hasMismatch,
                "orders", orderMismatches.size(),
                "statuses", statusMismatches.size(),
                "positions", positionMismatches.size()
        ));
        broadcastReport(hasMismatch, orderMismatches, statusMismatches, positionMismatches, state);
        return new ReconcileReport(hasMismatch, orderMismatches, statusMismatches, positionMismatches);
    }

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

    private void handleMismatch(Long userId, BrokerPort brokerPort, List<OrderSnapshot> dbOrders) {
        if (autoCancelPendingOnMismatch) {
            for (OrderSnapshot dbOrder : dbOrders) {
                if (dbOrder.open()) {
                    brokerPort.cancelOrder(userId, dbOrder.orderId());
                    OrderIntent intent = dbOrder.markCancelRequested();
                    if (intent != null) {
                        orderIntentRepository.save(intent);
                    }
                }
            }
        }
        if (autoFlattenOnMismatch) {
            log.warn("Auto-flatten requested but not configured; skipping");
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

    private static class OrderSnapshot {
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
            if (intent != null && intent.getOrderState() != OrderState.CANCEL_REQUESTED) {
                intent.transitionTo(OrderState.CANCEL_REQUESTED);
            }
            return intent;
        }
    }

    private record PositionSnapshot(String symbol, int netQty) {}
}
