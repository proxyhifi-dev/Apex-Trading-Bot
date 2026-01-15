package com.apex.backend.service;

import com.apex.backend.model.OrderState;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.List;


/**
 * Service responsible for placing and managing protective stop-loss orders
 * Ensures no position is left unprotected
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StopLossPlacementService {
    private final FyersService fyersService;
    private final TradeRepository tradeRepository;
    private final BroadcastService broadcastService;
    private final AlertService alertService;
    @Qualifier("tradingExecutor")
    private final Executor tradingExecutor;

    @Value("${execution.stop-ack-timeout-seconds:5}")
    private int stopAckTimeoutSeconds;

    /**
     * Place protective stop-loss order for a trade
     * @param trade The trade to protect
     * @param token FYERS access token
     * @return CompletableFuture that completes with true if stop was ACKED, false otherwise
     */
    public CompletableFuture<Boolean> placeProtectiveStop(Trade trade, String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Placing protective stop for trade {} symbol: {} stopPrice: {}", 
                    trade.getId(), trade.getSymbol(), trade.getCurrentStopLoss());
                
                String side = trade.getTradeType() == Trade.TradeType.LONG ? "SELL" : "BUY";
                String clientOrderId = "STOP-" + trade.getId() + "-" + System.currentTimeMillis();
                
                String stopOrderId = fyersService.placeStopLossOrder(
                    trade.getSymbol(),
                    trade.getQuantity(),
                    side,
                    trade.getCurrentStopLoss().doubleValue(),
                    clientOrderId,
                    token
                );
                
                trade.setStopOrderId(stopOrderId);
                trade.setStopOrderState(OrderState.SENT);
                tradeRepository.save(trade);
                
                log.info("Stop-loss order sent: {} brokerOrderId: {} for trade: {}", 
                    clientOrderId, stopOrderId, trade.getId());
                
                // Poll for ACK
                boolean acked = waitForStopAck(stopOrderId, token, stopAckTimeoutSeconds);
                if (acked) {
                    trade.setStopOrderState(OrderState.ACKED);
                    trade.setStopAckedAt(LocalDateTime.now());
                    tradeRepository.save(trade);
                    log.info("Stop-loss ACKED for trade: {} stopOrderId: {}", trade.getId(), stopOrderId);
                    broadcastService.broadcastOrders(List.of(trade));

                    return true;
                } else {
                    log.error("Stop-loss ACK timeout for trade: {} stopOrderId: {}", trade.getId(), stopOrderId);
                    trade.setStopOrderState(OrderState.UNKNOWN);
                    tradeRepository.save(trade);
                    return false;
                }
            } catch (Exception e) {
                log.error("Failed to place stop-loss for trade: {} symbol: {}", 
                    trade.getId(), trade.getSymbol(), e);
                trade.setStopOrderState(OrderState.REJECTED);
                tradeRepository.save(trade);
                return false;
            }
        }, tradingExecutor);
    }

    /**
     * Wait for stop-loss order to be ACKED by broker
     * @param stopOrderId Broker order ID
     * @param token FYERS access token
     * @param timeoutSeconds Timeout in seconds
     * @return true if ACKED within timeout, false otherwise
     */
    public boolean waitForStopAck(String stopOrderId, String token, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        int pollIntervalMs = 500; // Poll every 500ms
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                var orderDetails = fyersService.getOrderDetails(stopOrderId, token);
                if (orderDetails.isPresent()) {
                    String status = orderDetails.get().status();
                    if (status != null && !status.isBlank()) {
                        OrderState state = OrderState.fromString(status);
                        log.debug("Stop-loss order status: {} (state: {}) for orderId: {}", status, state, stopOrderId);
                        if (state == OrderState.REJECTED || state == OrderState.CANCELLED || state == OrderState.EXPIRED) {
                            return false;
                        }
                        return true; // Order exists and is not rejected/cancelled/expired = ACKED
                    }
                }
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                log.warn("Error checking stop-loss order status: {}", e.getMessage());
                // Continue polling
                try {
                    Thread.sleep(pollIntervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        
        return false; // Timeout
    }
}
