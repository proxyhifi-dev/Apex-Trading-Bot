# APEX TRADING BOT - PRODUCTION HARDENING IMPLEMENTATION PLAN

## EXECUTIVE SUMMARY

This document provides a file-by-file implementation plan to harden the Apex Trading Bot backend for production use. All changes are **incremental** and preserve existing API contracts. The plan addresses order lifecycle management, partial fills, timeouts, protective stops, reconciliation, risk enforcement, and crisis mode.

---

## ✅ Recent Updates (Implemented)
- **Watchlist v2**: DB-backed `watchlists` + `watchlist_items`, default watchlist per user, max 100 symbols, validation, and REST endpoints.
- **On-demand scanner runs**: `/api/scanner/run` creates a run, with status/results endpoints and diagnostics breakdown.
- **Security hardening**: public UI + auth endpoints, configurable `/actuator/health`, CORS via `APEX_ALLOWED_ORIGINS`.
- **Scheduler safety**: scanner scheduler gated by `apex.scanner.scheduler-enabled`, market window configurable.

---

## 1. ORDER LIFECYCLE STATE MACHINE (P0)

### A) Why Needed
**Failure Mode**: Current `OrderIntent.status` uses free-form strings ("PENDING", "PLACED", "FILLED") without state machine validation. No protection against invalid transitions (e.g., FILLED → PENDING). Missing states for partial fills, cancellations, and broker reconciliation.

**Impact**: Order state corruption, duplicate executions, inability to detect stuck/zombie orders.

### B) SAFE / REFACTOR-REQUIRED / DO-NOT-TOUCH
**SAFE** - Adding enum-based state machine with validation. Existing string statuses will be migrated.

### C) Files to Modify/Create

#### C1) Create: `backend/src/main/java/com/apex/backend/model/OrderState.java`
```java
public enum OrderState {
    CREATED,        // OrderIntent created, not yet sent to broker
    SENT,           // HTTP request sent to FYERS
    ACKED,          // Broker acknowledged (FYERS returned orderId)
    PART_FILLED,    // Partial fill received
    FILLED,         // Fully filled
    CANCEL_REQUESTED,// Cancel request sent
    CANCELLED,      // Broker confirmed cancellation
    REJECTED,       // Broker rejected
    EXPIRED,        // Timeout expired
    UNKNOWN;        // Broker status unknown/mismatch
    
    public boolean isTerminal() {
        return this == FILLED || this == CANCELLED || this == REJECTED || this == EXPIRED;
    }
    
    public boolean canTransitionTo(OrderState target) {
        // State machine rules
        return switch (this) {
            case CREATED -> target == SENT || target == REJECTED;
            case SENT -> target == ACKED || target == REJECTED || target == UNKNOWN;
            case ACKED -> target == PART_FILLED || target == FILLED || target == CANCELLED || target == REJECTED || target == EXPIRED;
            case PART_FILLED -> target == FILLED || target == CANCEL_REQUESTED || target == CANCELLED || target == EXPIRED;
            case CANCEL_REQUESTED -> target == CANCELLED || target == FILLED || target == UNKNOWN;
            default -> false;
        };
    }
}
```

#### C2) Create: `backend/src/main/java/com/apex/backend/model/SignalState.java`
```java
public enum SignalState {
    NEW,            // Signal created
    APPROVED,       // Risk checks passed, ready for execution
    REJECTED,       // Risk rejected
    EXPIRED;        // Signal expired (time-based)
}
```

#### C3) Create: `backend/src/main/java/com/apex/backend/model/PositionState.java`
```java
public enum PositionState {
    PLANNED,        // Trade planned, entry order not yet sent
    OPENING,        // Entry order sent, awaiting fill
    OPEN,           // Position open, stop-loss must be ACKED
    EXITING,        // Exit order sent
    CLOSED,         // Position closed
    ERROR;          // Error state (stop failed, mismatch, etc.)
}
```

#### C4) Modify: `backend/src/main/java/com/apex/backend/model/OrderIntent.java`
**Add fields:**
```java
@Enumerated(EnumType.STRING)
@Column(name = "order_state", nullable = false)
private OrderState orderState = OrderState.CREATED;

@Column(name = "last_broker_status")
private String lastBrokerStatus;  // Raw FYERS status string

@Column(name = "sent_at")
private LocalDateTime sentAt;

@Column(name = "acked_at")
private LocalDateTime ackedAt;

@Column(name = "expires_at")
private LocalDateTime expiresAt;

@Column(name = "correlation_id")
private String correlationId;  // For audit trail

@Column(name = "signal_id")
private Long signalId;  // Link to StockScreeningResult
```

**Migration**: Add `@Deprecated` to `status` field, keep for backward compatibility. Add method:
```java
public void transitionTo(OrderState newState) {
    if (!orderState.canTransitionTo(newState)) {
        throw new IllegalStateException("Invalid transition: " + orderState + " -> " + newState);
    }
    this.orderState = newState;
    this.status = newState.name(); // Keep legacy field in sync
    this.updatedAt = LocalDateTime.now();
}
```

#### C5) Modify: `backend/src/main/java/com/apex/backend/model/Trade.java`
**Add field:**
```java
@Enumerated(EnumType.STRING)
@Column(name = "position_state", nullable = false)
private PositionState positionState = PositionState.PLANNED;

@Column(name = "stop_order_id")
private String stopOrderId;  // Broker orderId for stop-loss

@Column(name = "stop_order_state")
@Enumerated(EnumType.STRING)
private OrderState stopOrderState;  // State of stop-loss order

@Column(name = "stop_acked_at")
private LocalDateTime stopAckedAt;
```

#### C6) Modify: `backend/src/main/java/com/apex/backend/service/ExecutionEngine.java`
**Update `execute()` method:**
- After creating OrderIntent: `intent.transitionTo(OrderState.CREATED)`
- After `fyersService.placeOrder()`: `intent.transitionTo(OrderState.SENT); intent.setSentAt(LocalDateTime.now())`
- After receiving brokerOrderId: `intent.transitionTo(OrderState.ACKED); intent.setAckedAt(LocalDateTime.now())`
- In `pollUntilTerminal()`: Use `OrderState` enum instead of string matching
- On timeout: `intent.transitionTo(OrderState.EXPIRED)`

**Update `applyPartialFill()`:**
- `intent.transitionTo(OrderState.PART_FILLED)`

**Update `applyFinalFill()`:**
- `intent.transitionTo(OrderState.FILLED)`

#### C7) Create: `backend/src/main/resources/db/migration/V12__order_state_machine.sql`
```sql
ALTER TABLE order_intents 
    ADD COLUMN order_state VARCHAR(50) NOT NULL DEFAULT 'CREATED',
    ADD COLUMN last_broker_status VARCHAR(50),
    ADD COLUMN sent_at TIMESTAMP,
    ADD COLUMN acked_at TIMESTAMP,
    ADD COLUMN expires_at TIMESTAMP,
    ADD COLUMN correlation_id VARCHAR(255),
    ADD COLUMN signal_id BIGINT;

ALTER TABLE trades
    ADD COLUMN position_state VARCHAR(50) NOT NULL DEFAULT 'PLANNED',
    ADD COLUMN stop_order_id VARCHAR(255),
    ADD COLUMN stop_order_state VARCHAR(50),
    ADD COLUMN stop_acked_at TIMESTAMP;

CREATE INDEX idx_order_intents_state ON order_intents(order_state);
CREATE INDEX idx_order_intents_expires ON order_intents(expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_trades_position_state ON trades(position_state);
```

### D) Method Signatures
```java
// In OrderIntent
public void transitionTo(OrderState newState) throws IllegalStateException

// In ExecutionEngine (new)
private void enforceStateTransition(OrderIntent intent, OrderState target)
```

### E) Config Keys
None (state machine is code-based).

### F) Tests
- `OrderStateTransitionTest.java`: Test all valid/invalid transitions
- `ExecutionEngineStateMachineTest.java`: Verify state transitions during execution
- `OrderIntentStatePersistenceTest.java`: Verify DB persistence of states

### G) Observable Signals
- Log: `"Order state transition: {} -> {}"` with correlationId
- Metric: `order_state_transitions_total{from,to}`
- WS Event: `ORDER_STATE_CHANGED` with `{orderId, oldState, newState, timestamp}`

---

## 2. PARTIAL FILL POLICY (P0)

### A) Why Needed
**Failure Mode**: Current code detects `PARTIALLY_FILLED` but continues polling indefinitely. No policy to cancel remainder or accept partial. Risk exposure unclear.

**Impact**: Unfilled quantity remains in market, unexpected positions, risk miscalculation.

### B) SAFE
**SAFE** - Adding config-driven policy with safe defaults.

### C) Files to Modify/Create

#### C1) Modify: `backend/src/main/resources/application.yml`
**Add section:**
```yaml
execution:
  partial-fill:
    policy: CANCEL_REMAIN  # CANCEL_REMAIN | KEEP_OPEN
    min-fill-pct: 80.0     # Below this: CANCEL_AND_FLATTEN
    cancel-timeout-seconds: 5
```

#### C2) Create: `backend/src/main/java/com/apex/backend/config/ExecutionProperties.java` (extend existing)
**Add nested class:**
```java
@Data
public static class PartialFillProperties {
    private PartialFillPolicy policy = PartialFillPolicy.CANCEL_REMAIN;
    private double minFillPct = 80.0;
    private int cancelTimeoutSeconds = 5;
}

public enum PartialFillPolicy {
    CANCEL_REMAIN,      // Cancel remaining qty immediately
    KEEP_OPEN,         // Keep order open until timeout
    CANCEL_AND_FLATTEN // Cancel and flatten position (safe default for low fill %)
}
```

#### C3) Modify: `backend/src/main/java/com/apex/backend/service/ExecutionEngine.java`
**Update `applyPartialFill()` method:**
```java
private void applyPartialFill(OrderIntent intent, FyersOrderStatus status) {
    // ... existing fill update logic ...
    intent.transitionTo(OrderState.PART_FILLED);
    
    int filledQty = status.filledQuantity();
    int totalQty = intent.getQuantity();
    double fillPct = (filledQty * 100.0) / totalQty;
    
    PartialFillPolicy policy = executionProperties.getPartialFill().getPolicy();
    if (fillPct < executionProperties.getPartialFill().getMinFillPct()) {
        policy = PartialFillPolicy.CANCEL_AND_FLATTEN;
    }
    
    switch (policy) {
        case CANCEL_REMAIN:
            cancelRemainingQuantity(intent, filledQty);
            break;
        case CANCEL_AND_FLATTEN:
            cancelRemainingQuantity(intent, filledQty);
            // Mark position for flatten (handled by reconciliation)
            break;
        case KEEP_OPEN:
            // Continue polling (existing behavior)
            break;
    }
    
    // Emit WS event
    broadcastService.broadcastOrders(/* ... */);
}
```

**Add method:**
```java
private void cancelRemainingQuantity(OrderIntent intent, int filledQty) {
    int remainingQty = intent.getQuantity() - filledQty;
    if (remainingQty <= 0) return;
    
    intent.transitionTo(OrderState.CANCEL_REQUESTED);
    try {
        String cancelOrderId = fyersService.cancelOrder(intent.getBrokerOrderId(), /* token */);
        // Poll for cancel confirmation
        // On success: intent.transitionTo(OrderState.CANCELLED)
    } catch (Exception e) {
        log.error("Failed to cancel remaining qty for order {}", intent.getClientOrderId(), e);
        // Enter safe mode or alert
    }
}
```

#### C4) Modify: `backend/src/main/java/com/apex/backend/service/FyersService.java`
**Add method:**
```java
public String cancelOrder(String brokerOrderId, String token) {
    String resolvedToken = resolveToken(token);
    String url = apiBaseUrl + "/orders/" + brokerOrderId;
    // HTTP DELETE or POST with cancel action
    // Return cancel confirmation orderId
}
```

### D) Method Signatures
```java
// In ExecutionEngine
private void cancelRemainingQuantity(OrderIntent intent, int filledQty)
private void handlePartialFillPolicy(OrderIntent intent, double fillPct)

// In FyersService
public String cancelOrder(String brokerOrderId, String token)
```

### E) Config Keys
```properties
execution.partial-fill.policy=CANCEL_REMAIN
execution.partial-fill.min-fill-pct=80.0
execution.partial-fill.cancel-timeout-seconds=5
```

### F) Tests
- `PartialFillCancelRemainTest.java`: Verify cancel on partial fill
- `PartialFillMinFillPctTest.java`: Verify CANCEL_AND_FLATTEN below threshold
- `PartialFillKeepOpenTest.java`: Verify KEEP_OPEN policy

### G) Observable Signals
- Log: `"Partial fill: {}% filled, policy: {}, action: {}"`
- WS Event: `ORDER_PARTIAL_CANCEL_REMAIN` with `{orderId, filledQty, remainingQty, fillPct}`
- Metric: `partial_fills_total{policy}`

---

## 3. ORDER TIMEOUTS + CANCEL (P0)

### A) Why Needed
**Failure Mode**: `pollUntilTerminal()` uses fixed `maxPollAttempts` (12) with 1s delay = 12s max. No timeout per order. LIMIT orders can hang indefinitely if not filled.

**Impact**: Stuck orders, capital locked, risk exposure unclear.

### B) SAFE
**SAFE** - Adding timeout config with cancel-on-timeout behavior.

### C) Files to Modify/Create

#### C1) Modify: `backend/src/main/resources/application.yml`
**Add to execution section:**
```yaml
execution:
  entry-timeout-seconds: 30
  stop-ack-timeout-seconds: 5
  cancel-on-timeout: true
```

#### C2) Modify: `backend/src/main/java/com/apex/backend/service/ExecutionEngine.java`
**Update `execute()` method:**
```java
// After creating OrderIntent
intent.setExpiresAt(LocalDateTime.now().plusSeconds(executionProperties.getEntryTimeoutSeconds()));
```

**Update `pollUntilTerminal()` method:**
```java
private ExecutionResult pollUntilTerminal(OrderIntent intent, String token) {
    int attempts = 0;
    while (attempts < maxPollAttempts) {
        // Check timeout
        if (intent.getExpiresAt() != null && LocalDateTime.now().isAfter(intent.getExpiresAt())) {
            log.warn("Order {} timed out", intent.getClientOrderId());
            intent.transitionTo(OrderState.EXPIRED);
            
            if (executionProperties.isCancelOnTimeout()) {
                try {
                    fyersService.cancelOrder(intent.getBrokerOrderId(), token);
                    intent.transitionTo(OrderState.CANCEL_REQUESTED);
                } catch (Exception e) {
                    log.error("Failed to cancel timed-out order", e);
                }
            }
            
            return toResult(intent, ExecutionStatus.EXPIRED);
        }
        
        // ... existing polling logic ...
    }
    
    // Max attempts reached
    if (intent.getExpiresAt() != null && LocalDateTime.now().isAfter(intent.getExpiresAt())) {
        intent.transitionTo(OrderState.EXPIRED);
    } else {
        intent.transitionTo(OrderState.UNKNOWN);
    }
    return toResult(intent, ExecutionStatus.UNKNOWN);
}
```

#### C3) Create: `backend/src/main/java/com/apex/backend/service/StopLossPlacementService.java`
```java
@Service
@RequiredArgsConstructor
public class StopLossPlacementService {
    private final FyersService fyersService;
    private final ExecutionEngine executionEngine;
    private final BroadcastService broadcastService;
    private final SettingsService settingsService;
    
    @Value("${execution.stop-ack-timeout-seconds:5}")
    private int stopAckTimeoutSeconds;
    
    public CompletableFuture<Boolean> placeProtectiveStop(Trade trade, String token) {
        // Place stop-loss order via FyersService
        // Poll for ACK within stopAckTimeoutSeconds
        // On timeout: return false (triggers flatten)
        // On success: update trade.stopOrderId, trade.stopOrderState = ACKED
    }
}
```

### D) Method Signatures
```java
// In ExecutionEngine
private boolean checkOrderTimeout(OrderIntent intent)

// In StopLossPlacementService (new)
public CompletableFuture<Boolean> placeProtectiveStop(Trade trade, String token)
public boolean waitForStopAck(String stopOrderId, String token, int timeoutSeconds)
```

### E) Config Keys
```properties
execution.entry-timeout-seconds=30
execution.stop-ack-timeout-seconds=5
execution.cancel-on-timeout=true
```

### F) Tests
- `OrderTimeoutTest.java`: Verify EXPIRED state on timeout
- `OrderTimeoutCancelTest.java`: Verify cancel on timeout
- `StopLossTimeoutTest.java`: Verify flatten on stop timeout

### G) Observable Signals
- Log: `"Order timeout: {}, action: {}"`
- WS Event: `ORDER_TIMEOUT` with `{orderId, timeoutSeconds, action}`
- Alert: `ORDER_TIMEOUT` with order details

---

## 4. PROTECTIVE STOP GUARANTEE (P0)

### A) Why Needed
**Failure Mode**: After entry fill, stop-loss placement is **not implemented**. Trade is created with `stopLoss` field but no broker order is placed. Position is unprotected.

**Impact**: Unprotected positions, unlimited loss exposure, regulatory violation.

### B) REFACTOR-REQUIRED
**REFACTOR-REQUIRED** - This is a critical missing feature. Must be implemented before live trading.

### C) Files to Modify/Create

#### C1) Modify: `backend/src/main/java/com/apex/backend/service/TradeExecutionService.java`
**Update `approveAndExecute()` method:**
```java
// After executionResult.status() == FILLED
if (executionResult.status() == ExecutionEngine.ExecutionStatus.FILLED) {
    // ... existing trade creation ...
    trade.setPositionState(PositionState.OPENING);
    tradeRepo.save(trade);
    
    // PLACE PROTECTIVE STOP IMMEDIATELY
    if (!isPaper) {
        String token = fyersAuthService.getFyersToken(userId);
        boolean stopPlaced = stopLossPlacementService.placeProtectiveStop(trade, token).join();
        
        if (!stopPlaced) {
            // STOP PLACEMENT FAILED - ENTER ERROR STATE
            trade.setPositionState(PositionState.ERROR);
            tradeRepo.save(trade);
            
            // Attempt immediate flatten
            flattenPosition(trade, token);
            
            // Halt new trading
            circuitBreakerService.triggerGlobalHalt("STOP_LOSS_PLACEMENT_FAILED: " + trade.getSymbol());
            
            // Emit alert
            alertService.sendAlert("STOP_FAILED", "Failed to place stop for " + trade.getSymbol());
            return;
        }
        
        // Stop placed successfully
        trade.setPositionState(PositionState.OPEN);
    } else {
        // Paper mode: simulate stop placement
        trade.setStopOrderId("PAPER-STOP-" + UUID.randomUUID());
        trade.setStopOrderState(OrderState.ACKED);
        trade.setPositionState(PositionState.OPEN);
    }
    
    tradeRepo.save(trade);
}
```

**Add method:**
```java
private void flattenPosition(Trade trade, String token) {
    try {
        ExecutionEngine.ExecutionRequestPayload exitRequest = new ExecutionEngine.ExecutionRequestPayload(
            trade.getUserId(),
            trade.getSymbol(),
            trade.getQuantity(),
            ExecutionCostModel.OrderType.MARKET,
            trade.getTradeType() == Trade.TradeType.LONG ? ExecutionSide.SELL : ExecutionSide.BUY,
            null,
            false,
            "FLATTEN-" + trade.getId(),
            trade.getAtr() != null ? trade.getAtr().doubleValue() : 0.0,
            null,
            trade.getEntryPrice().doubleValue(),
            null,
            true  // exitOrder = true
        );
        executionEngine.execute(exitRequest);
    } catch (Exception e) {
        log.error("Failed to flatten position {}", trade.getId(), e);
        alertService.sendAlert("FLATTEN_FAILED", "Failed to flatten " + trade.getSymbol());
    }
}
```

#### C2) Create: `backend/src/main/java/com/apex/backend/service/StopLossPlacementService.java`
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class StopLossPlacementService {
    private final FyersService fyersService;
    private final FyersAuthService fyersAuthService;
    private final BroadcastService broadcastService;
    
    @Value("${execution.stop-ack-timeout-seconds:5}")
    private int stopAckTimeoutSeconds;
    
    public CompletableFuture<Boolean> placeProtectiveStop(Trade trade, String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String stopOrderId = fyersService.placeStopLossOrder(
                    trade.getSymbol(),
                    trade.getQuantity(),
                    trade.getTradeType() == Trade.TradeType.LONG ? "SELL" : "BUY",
                    trade.getCurrentStopLoss().doubleValue(),
                    "STOP-" + trade.getId(),
                    token
                );
                
                trade.setStopOrderId(stopOrderId);
                trade.setStopOrderState(OrderState.SENT);
                // ... save trade ...
                
                // Poll for ACK
                boolean acked = waitForStopAck(stopOrderId, token, stopAckTimeoutSeconds);
                if (acked) {
                    trade.setStopOrderState(OrderState.ACKED);
                    trade.setStopAckedAt(LocalDateTime.now());
                    // ... save trade ...
                    return true;
                } else {
                    log.error("Stop-loss ACK timeout for trade {}", trade.getId());
                    return false;
                }
            } catch (Exception e) {
                log.error("Failed to place stop-loss for trade {}", trade.getId(), e);
                return false;
            }
        });
    }
    
    private boolean waitForStopAck(String stopOrderId, String token, int timeoutSeconds) {
        // Poll FYERS for order status
        // Return true if ACKED within timeout
    }
}
```

#### C3) Modify: `backend/src/main/java/com/apex/backend/service/FyersService.java`
**Add method:**
```java
public String placeStopLossOrder(String symbol, int qty, String side, double stopPrice, String clientOrderId, String token) {
    String resolvedToken = resolveToken(token);
    String url = apiBaseUrl + "/orders";
    Map<String, Object> body = new HashMap<>();
    body.put("symbol", symbol);
    body.put("qty", qty);
    body.put("side", side.equalsIgnoreCase("BUY") ? 1 : -1);
    body.put("type", 3); // STOP_LOSS order type in FYERS
    body.put("stopPrice", stopPrice);
    body.put("productType", "INTRADAY");
    body.put("validity", "DAY");
    body.put("clientId", clientOrderId);
    
    String response = fyersHttpClient.post(url, resolvedToken, gson.toJson(body));
    JsonObject json = JsonParser.parseString(response).getAsJsonObject();
    if (json.has("id")) {
        return json.get("id").getAsString();
    }
    throw new RuntimeException("Stop-loss order placement failed");
}
```

### D) Method Signatures
```java
// In StopLossPlacementService (new)
public CompletableFuture<Boolean> placeProtectiveStop(Trade trade, String token)
private boolean waitForStopAck(String stopOrderId, String token, int timeoutSeconds)

// In FyersService
public String placeStopLossOrder(String symbol, int qty, String side, double stopPrice, String clientOrderId, String token)

// In TradeExecutionService
private void flattenPosition(Trade trade, String token)
```

### E) Config Keys
```properties
execution.stop-ack-timeout-seconds=5
```

### F) Tests
- `StopLossPlacementTest.java`: Verify stop placement after entry fill
- `StopLossPlacementFailureTest.java`: Verify ERROR state and flatten on failure
- `StopLossTimeoutTest.java`: Verify flatten on ACK timeout

### G) Observable Signals
- Log: `"Protective stop placed: orderId={}, tradeId={}"`
- Log: `"Stop placement failed: tradeId={}, action=FLATTEN"`
- WS Event: `STOP_PLACED` / `STOP_FAILED`
- Alert: `STOP_FAILED` with trade details

---

## 5. STARTUP + PERIODIC RECONCILIATION (P0)

### A) Why Needed
**Failure Mode**: No reconciliation between DB state and FYERS broker state. Ghost orders (broker has, DB doesn't), zombie orders (DB expects, broker doesn't), orphan positions.

**Impact**: State corruption, duplicate orders, risk miscalculation, regulatory issues.

### B) SAFE
**SAFE** - New service, no breaking changes.

### C) Files to Modify/Create

#### C1) Create: `backend/src/main/java/com/apex/backend/service/ReconciliationService.java`
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {
    private final FyersService fyersService;
    private final OrderIntentRepository orderIntentRepository;
    private final TradeRepository tradeRepository;
    private final FyersAuthService fyersAuthService;
    private final SettingsService settingsService;
    private final BroadcastService broadcastService;
    private final AlertService alertService;
    
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
            reconcile();
        }
    }
    
    @Scheduled(fixedDelayString = "${reconcile.interval-seconds}000")
    public void periodicReconcile() {
        if (enabled) {
            reconcile();
        }
    }
    
    public ReconciliationResult reconcile() {
        ReconciliationResult result = new ReconciliationResult();
        
        // Fetch open orders from FYERS
        List<FyersOrder> brokerOrders = fyersService.getOpenOrders(/* token */);
        
        // Fetch expected orders from DB
        List<OrderIntent> dbOrders = orderIntentRepository.findByOrderStateIn(
            List.of(OrderState.SENT, OrderState.ACKED, OrderState.PART_FILLED)
        );
        
        // Detect mismatches
        detectGhostOrders(brokerOrders, dbOrders, result);
        detectZombieOrders(brokerOrders, dbOrders, result);
        detectOrphanPositions(result);
        
        // Take actions
        if (result.hasMismatches() && safeModeOnMismatch) {
            enterSafeMode(result);
        }
        
        // Emit events
        broadcastReconciliationResult(result);
        
        return result;
    }
    
    private void detectGhostOrders(List<FyersOrder> brokerOrders, List<OrderIntent> dbOrders, ReconciliationResult result) {
        Set<String> dbBrokerOrderIds = dbOrders.stream()
            .map(OrderIntent::getBrokerOrderId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        
        for (FyersOrder brokerOrder : brokerOrders) {
            if (!dbBrokerOrderIds.contains(brokerOrder.getId())) {
                result.addGhostOrder(brokerOrder);
                // Create OrderIntent for ghost order
                createOrderIntentForGhost(brokerOrder);
            }
        }
    }
    
    private void detectZombieOrders(List<FyersOrder> brokerOrders, List<OrderIntent> dbOrders, ReconciliationResult result) {
        Set<String> brokerOrderIds = brokerOrders.stream()
            .map(FyersOrder::getId)
            .collect(Collectors.toSet());
        
        for (OrderIntent dbOrder : dbOrders) {
            if (dbOrder.getBrokerOrderId() != null && !brokerOrderIds.contains(dbOrder.getBrokerOrderId())) {
                result.addZombieOrder(dbOrder);
                // Mark as UNKNOWN or attempt cancel
                dbOrder.transitionTo(OrderState.UNKNOWN);
                orderIntentRepository.save(dbOrder);
            }
        }
    }
    
    private void enterSafeMode(ReconciliationResult result) {
        // Set apex.trading.enabled=false (persisted)
        settingsService.setTradingEnabled(false);
        alertService.sendAlert("RECONCILIATION_MISMATCH", result.getSummary());
        broadcastService.broadcastBotStatus(/* HALTED status */);
    }
    
    public record ReconciliationResult(
        List<FyersOrder> ghostOrders,
        List<OrderIntent> zombieOrders,
        List<Trade> orphanPositions,
        boolean hasMismatches
    ) {
        public String getSummary() {
            return String.format("Ghost: %d, Zombie: %d, Orphan: %d",
                ghostOrders.size(), zombieOrders.size(), orphanPositions.size());
        }
    }
}
```

#### C2) Modify: `backend/src/main/java/com/apex/backend/service/FyersService.java`
**Add method:**
```java
public List<FyersOrder> getOpenOrders(String token) {
    String resolvedToken = resolveToken(token);
    String url = apiBaseUrl + "/orders";
    // Fetch all open orders from FYERS
    // Parse and return List<FyersOrder>
}
```

#### C3) Modify: `backend/src/main/resources/application.yml`
**Add section:**
```yaml
reconcile:
  enabled: true
  interval-seconds: 300
  safe-mode-on-mismatch: true
```

### D) Method Signatures
```java
// In ReconciliationService (new)
public ReconciliationResult reconcile()
private void detectGhostOrders(...)
private void detectZombieOrders(...)
private void enterSafeMode(ReconciliationResult result)

// In FyersService
public List<FyersOrder> getOpenOrders(String token)
```

### E) Config Keys
```properties
reconcile.enabled=true
reconcile.interval-seconds=300
reconcile.safe-mode-on-mismatch=true
```

### F) Tests
- `ReconciliationGhostOrderTest.java`: Verify ghost order detection
- `ReconciliationZombieOrderTest.java`: Verify zombie order detection
- `ReconciliationSafeModeTest.java`: Verify safe mode on mismatch

### G) Observable Signals
- Log: `"Reconciliation: Ghost={}, Zombie={}, Orphan={}"`
- WS Event: `RECONCILIATION_MISMATCH` with details
- Alert: `RECONCILIATION_MISMATCH` with summary

---

## 6. RISK ENFORCEMENT AS TRUE SOURCE OF TRUTH (P0)

### A) Why Needed
**Failure Mode**: `RiskGatekeeper` returns boolean `allowed` with string `reason`, but no structured reject codes. UI cannot display threshold/current values. Missing codes for cooldown, drawdown, crisis mode.

**Impact**: Poor UX, unclear rejection reasons, missing risk controls.

### B) SAFE
**SAFE** - Extending existing enum, adding fields to response.

### C) Files to Modify/Create

#### C1) Modify: `backend/src/main/java/com/apex/backend/service/RiskGatekeeper.java`
**Extend `RiskRejectCode` enum:**
```java
public enum RiskRejectCode {
    PORTFOLIO_HEAT_LIMIT,
    DAILY_LOSS_LIMIT,
    CORRELATION_LIMIT,
    MAX_OPEN_POSITIONS,
    SPREAD_TOO_WIDE,
    LIQUIDITY_DATA_MISSING,
    MAX_POSITIONS,           // NEW
    COOLDOWN,                // NEW
    DRAWDOWN_LIMIT,          // NEW
    CONSEC_LOSSES,           // NEW
    CRISIS_MODE,             // NEW
    DATA_STALE,              // NEW
    CORP_ACTION_BLACKOUT,     // NEW
    MARKET_CLOSED,           // NEW
    MANUAL_HALTED;           // NEW
}
```

**Modify `RiskGateDecision` record:**
```java
public record RiskGateDecision(
    boolean allowed,
    RiskRejectCode reason,
    String message,
    Double threshold,      // NEW
    Double currentValue,   // NEW
    String symbol,         // NEW
    Long signalId          // NEW
) {
    static RiskGateDecision allow() {
        return new RiskGateDecision(true, null, null, null, null, null, null);
    }
    
    static RiskGateDecision reject(RiskRejectCode reason, String message, Double threshold, Double currentValue, String symbol, Long signalId) {
        return new RiskGateDecision(false, reason, message, threshold, currentValue, symbol, signalId);
    }
}
```

**Update `evaluate()` method to include threshold/current:**
```java
if (openPositions >= strategyConfig.getRisk().getMaxOpenPositions()) {
    return RiskGateDecision.reject(
        RiskRejectCode.MAX_OPEN_POSITIONS,
        "Max open positions reached",
        (double) strategyConfig.getRisk().getMaxOpenPositions(),
        (double) openPositions,
        request.symbol(),
        null
    );
}
```

#### C2) Modify: `backend/src/main/java/com/apex/backend/service/ExecutionEngine.java`
**Update rejection handling:**
```java
if (!riskDecision.allowed()) {
    // ... save rejected OrderIntent ...
    
    // Emit WS event with full details
    broadcastService.broadcastReject(new RejectEvent(
        riskDecision.reason().name(),
        riskDecision.threshold(),
        riskDecision.currentValue(),
        riskDecision.symbol(),
        riskDecision.signalId(),
        clientOrderId
    ));
    
    return new ExecutionResult(...);
}
```

#### C3) Create: `backend/src/main/java/com/apex/backend/service/BroadcastService.java` (extend existing)
**Add method:**
```java
public void broadcastReject(RejectEvent event) {
    messagingTemplate.convertAndSend("/topic/rejects", event);
    metricsService.incrementWebsocketPublishes();
}

public record RejectEvent(
    String reasonCode,
    Double threshold,
    Double currentValue,
    String symbol,
    Long signalId,
    String clientOrderId,
    LocalDateTime timestamp
) {
    public RejectEvent(String reasonCode, Double threshold, Double currentValue, String symbol, Long signalId, String clientOrderId) {
        this(reasonCode, threshold, currentValue, symbol, signalId, clientOrderId, LocalDateTime.now());
    }
}
```

#### C4) Add cooldown check to `RiskGatekeeper.evaluate()`:
```java
// Check symbol cooldown
if (tradeCooldownService.isInCooldown(request.symbol(), request.userId())) {
    long remainingSeconds = tradeCooldownService.getRemainingCooldown(request.symbol(), request.userId());
    return RiskGateDecision.reject(
        RiskRejectCode.COOLDOWN,
        "Symbol in cooldown",
        null,
        (double) remainingSeconds,
        request.symbol(),
        null
    );
}
```

### D) Method Signatures
```java
// In RiskGatekeeper (modified)
static RiskGateDecision reject(RiskRejectCode reason, String message, Double threshold, Double currentValue, String symbol, Long signalId)

// In BroadcastService (new)
public void broadcastReject(RejectEvent event)
```

### E) Config Keys
```properties
risk.trade-cooldown.minutes=5
```

### F) Tests
- `RiskGatekeeperRejectCodesTest.java`: Verify all reject codes are emitted
- `RiskGatekeeperThresholdTest.java`: Verify threshold/current values in reject

### G) Observable Signals
- WS Event: `REJECT` with `{reasonCode, threshold, currentValue, symbol, signalId}`
- Log: `"Risk reject: {} (threshold: {}, current: {})"`

---

## 7. CRISIS MODE (P1)

### A) Why Needed
**Failure Mode**: No market-wide crisis detection. Extreme market moves (NIFTY drop >5%, VIX >30) should halt trading.

**Impact**: Trading during extreme volatility, large losses, regulatory issues.

### B) SAFE
**SAFE** - New service, opt-in via config.

### C) Files to Modify/Create

#### C1) Create: `backend/src/main/java/com/apex/backend/service/CrisisModeService.java`
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class CrisisModeService {
    private final FyersService fyersService;
    private final CircuitBreakerService circuitBreakerService;
    private final BroadcastService broadcastService;
    
    @Value("${crisis.enabled:true}")
    private boolean enabled;
    
    @Value("${crisis.index-symbol:NSE:NIFTY50-EQ}")
    private String indexSymbol;
    
    @Value("${crisis.drop-threshold-pct:5.0}")
    private double dropThresholdPct;
    
    @Value("${crisis.vix-threshold:30.0}")
    private double vixThreshold;
    
    @Value("${crisis.halt-duration-minutes:30}")
    private int haltDurationMinutes;
    
    @Scheduled(fixedDelay = 60000) // Every minute
    public void checkCrisisMode() {
        if (!enabled) return;
        
        // Check NIFTY drop
        double niftyChange = getNiftyChangePct();
        if (niftyChange < -dropThresholdPct) {
            triggerCrisisMode("NIFTY_DROP", String.format("NIFTY dropped %.2f%%", Math.abs(niftyChange)));
            return;
        }
        
        // Check VIX (if available)
        // double vix = getVix();
        // if (vix > vixThreshold) {
        //     triggerCrisisMode("VIX_SPIKE", String.format("VIX at %.2f", vix));
        // }
    }
    
    private void triggerCrisisMode(String reason, String detail) {
        circuitBreakerService.pauseTrading("CRISIS: " + reason, null);
        broadcastService.broadcastBotStatus(new CrisisModeEvent(true, reason, detail, LocalDateTime.now()));
        
        // Schedule auto-resume
        // ScheduledExecutorService.schedule(() -> resume(), haltDurationMinutes, TimeUnit.MINUTES);
    }
}
```

#### C2) Modify: `backend/src/main/resources/application.yml`
**Add section:**
```yaml
crisis:
  enabled: true
  index-symbol: NSE:NIFTY50-EQ
  drop-threshold-pct: 5.0
  vix-threshold: 30.0
  halt-duration-minutes: 30
```

### D) Method Signatures
```java
// In CrisisModeService (new)
public void checkCrisisMode()
private void triggerCrisisMode(String reason, String detail)
```

### E) Config Keys
```properties
crisis.enabled=true
crisis.index-symbol=NSE:NIFTY50-EQ
crisis.drop-threshold-pct=5.0
crisis.vix-threshold=30.0
crisis.halt-duration-minutes=30
```

### F) Tests
- `CrisisModeNiftyDropTest.java`: Verify halt on NIFTY drop
- `CrisisModeAutoResumeTest.java`: Verify auto-resume after duration

### G) Observable Signals
- WS Event: `CRISIS_MODE_ON` with `{reason, detail, timestamp}`
- Alert: `CRISIS_MODE` with details

---

## 8. DATA QUALITY GUARDS (P1)

### A) Why Needed
**Failure Mode**: `DataQualityGuard` exists but reject reasons are not structured. Missing `DATA_STALE` and `DATA_GAP` codes.

**Impact**: Trading on stale/gappy data, poor signal quality.

### B) SAFE
**SAFE** - Extending existing service.

### C) Files to Modify/Create

#### C1) Modify: `backend/src/main/java/com/apex/backend/service/DataQualityGuard.java`
**Update `validate()` to return structured result:**
```java
public DataQualityResult validate(String timeframe, List<Candle> candles) {
    // ... existing validation ...
    
    // Check staleness
    if (candles != null && !candles.isEmpty()) {
        LocalDateTime latest = candles.get(candles.size() - 1).getTimestamp();
        long ageSeconds = Duration.between(latest, LocalDateTime.now()).getSeconds();
        if (ageSeconds > properties.getMaxStaleSeconds()) {
            reasons.add("DATA_STALE: " + ageSeconds + "s old");
            return new DataQualityResult(false, reasons, DataQualityIssue.STALE);
        }
    }
    
    // Check gaps (existing logic)
    if (missingCount > properties.getMaxMissingCandles()) {
        reasons.add("DATA_GAP: " + missingCount + " missing candles");
        return new DataQualityResult(false, reasons, DataQualityIssue.GAP);
    }
    
    return new DataQualityResult(true, reasons, null);
}

public enum DataQualityIssue {
    STALE, GAP, OUTLIER, INSUFFICIENT
}
```

#### C2) Modify: `backend/src/main/resources/application.yml`
**Add to data-quality section:**
```yaml
data-quality:
  max-stale-seconds: 300  # 5 minutes
```

### D) Method Signatures
```java
// In DataQualityGuard (modified)
public DataQualityResult validate(String timeframe, List<Candle> candles)
```

### E) Config Keys
```properties
data-quality.max-stale-seconds=300
```

### F) Tests
- `DataQualityStaleTest.java`: Verify STALE rejection
- `DataQualityGapTest.java`: Verify GAP rejection

### G) Observable Signals
- Log: `"Data quality reject: {}"`
- WS Event: `DATA_QUALITY_REJECT` with issue type

---

## 9. CONFIG CONSOLIDATION (REQUIRED)

### A) Why Needed
**Failure Mode**: Project uses both `application.properties` and `application.yml`. Keys are duplicated/inconsistent.

**Impact**: Confusion, wrong values used, maintenance burden.

### B) REFACTOR-REQUIRED
**REFACTOR-REQUIRED** - Must consolidate to one format.

### C) Files to Modify/Create

#### C1) Migration Plan
1. **Keep `application.yml` as canonical** (more readable for nested config)
2. **Move all keys from `application.properties` to `application.yml`**
3. **Delete `application.properties`** (or keep minimal overrides)
4. **Create `application-dev.yml` and `application-prod.yml`** for environment-specific overrides

#### C2) New Structure
```
src/main/resources/
  application.yml          # Base config (all keys)
  application-dev.yml      # Dev overrides
  application-prod.yml     # Prod overrides
```

#### C3) Namespace Consistency
All new keys follow pattern:
- `execution.*` - Order execution
- `risk.*` - Risk management
- `crisis.*` - Crisis mode
- `reconcile.*` - Reconciliation
- `data.*` - Data quality
- `india.*` - India-specific (market hours, holidays)

### D) Migration Notes
1. Copy all keys from `application.properties` to `application.yml`
2. Test that existing `@Value` annotations still work
3. Update documentation
4. Remove `application.properties` in next major version

---

## 10. TESTING PLAN

### High-Value Tests

1. **OrderStateTransitionTest.java**
   - Test all valid transitions
   - Test invalid transitions throw exception
   - Test terminal states cannot transition

2. **PartialFillCancelRemainTest.java**
   - Mock partial fill
   - Verify cancel request sent
   - Verify state transitions

3. **OrderTimeoutTest.java**
   - Set short timeout
   - Verify EXPIRED state
   - Verify cancel on timeout

4. **StopLossPlacementFailureTest.java**
   - Mock stop placement failure
   - Verify ERROR state
   - Verify flatten attempt
   - Verify halt triggered

5. **ReconciliationMismatchTest.java**
   - Create ghost/zombie orders
   - Run reconciliation
   - Verify safe mode entered
   - Verify WS events emitted

6. **RiskRejectCodesTest.java**
   - Test all reject codes
   - Verify threshold/current values
   - Verify WS events

---

## 11. ROLLOUT PLAN

### Phase 1: Dev Environment (Week 1)
- Implement order state machine
- Implement partial fill policy
- Implement order timeouts
- **Test in paper mode only**

### Phase 2: Paper Trading (Week 2)
- Implement protective stop placement
- Implement reconciliation (startup only)
- **Monitor for 1 week in paper mode**

### Phase 3: Small Live (Week 3)
- Enable reconciliation (periodic)
- Enable crisis mode
- **Start with 1 position max, small size**

### Phase 4: Full Production (Week 4)
- All features enabled
- Full monitoring
- **Gradual ramp-up**

---

## 12. GO/NO-GO CHECKLIST

### Pre-Production Readiness

- [ ] Order state machine implemented and tested
- [ ] Partial fill policy tested (CANCEL_REMAIN)
- [ ] Order timeouts tested (30s entry, 5s stop)
- [ ] Protective stop placement tested (success + failure paths)
- [ ] Reconciliation tested (ghost/zombie detection)
- [ ] Risk reject codes tested (all codes emit correctly)
- [ ] Crisis mode tested (NIFTY drop trigger)
- [ ] Data quality guards tested (STALE/GAP)
- [ ] Config consolidated (application.yml only)
- [ ] All unit tests passing
- [ ] Integration tests passing
- [ ] Paper mode tested for 1 week
- [ ] Monitoring/alerting verified
- [ ] WS events verified in UI
- [ ] Documentation updated

### Production Hardening Complete When:
- ✅ All P0 items implemented
- ✅ All tests passing
- ✅ Paper mode validated for 1 week
- ✅ Monitoring dashboards ready
- ✅ Runbook for safe mode recovery

---

## APPENDIX: FILE CHANGE SUMMARY

### New Files (15)
1. `OrderState.java`
2. `SignalState.java`
3. `PositionState.java`
4. `StopLossPlacementService.java`
5. `ReconciliationService.java`
6. `CrisisModeService.java`
7. `V12__order_state_machine.sql`
8. Test files (8)

### Modified Files (12)
1. `OrderIntent.java` - Add state machine fields
2. `Trade.java` - Add position state, stop order tracking
3. `ExecutionEngine.java` - State transitions, timeouts, partial fills
4. `FyersService.java` - Cancel order, stop-loss order, get open orders
5. `TradeExecutionService.java` - Stop placement after fill
6. `RiskGatekeeper.java` - Extended reject codes, threshold/current values
7. `BroadcastService.java` - Reject events
8. `DataQualityGuard.java` - Structured reject reasons
9. `ExecutionProperties.java` - Partial fill config
10. `application.yml` - All new config keys
11. `application.properties` - Marked for removal
12. Test files

### Config Keys Added (20+)
- `execution.partial-fill.*` (3 keys)
- `execution.entry-timeout-seconds`
- `execution.stop-ack-timeout-seconds`
- `execution.cancel-on-timeout`
- `reconcile.*` (3 keys)
- `crisis.*` (5 keys)
- `data-quality.max-stale-seconds`
- `risk.trade-cooldown.minutes`

---

**END OF IMPLEMENTATION PLAN**

---

## Roadmap / Remaining Work
- Complete order lifecycle endpoints (`GET /api/orders`, filters, lifecycle events).
- Add broker connectivity health indicator (soft-fail) to `/actuator/health`.
- Add integration tests for scanner runs and order lifecycle transitions.
- Expand OpenAPI annotations to cover all endpoints and error responses.
