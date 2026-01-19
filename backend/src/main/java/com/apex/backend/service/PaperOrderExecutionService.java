package com.apex.backend.service;

import com.apex.backend.dto.OrderModifyRequest;
import com.apex.backend.dto.PlaceOrderRequest;
import com.apex.backend.exception.BadRequestException;
import com.apex.backend.exception.ConflictException;
import com.apex.backend.exception.NotFoundException;
import com.apex.backend.model.PaperAccount;
import com.apex.backend.model.PaperOrder;
import com.apex.backend.model.PaperPosition;
import com.apex.backend.model.PaperTrade;
import com.apex.backend.repository.PaperAccountRepository;
import com.apex.backend.repository.PaperOrderRepository;
import com.apex.backend.repository.PaperPositionRepository;
import com.apex.backend.repository.PaperTradeRepository;
import com.apex.backend.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaperOrderExecutionService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_FILLED = "FILLED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final PaperOrderRepository orderRepository;
    private final PaperTradeRepository tradeRepository;
    private final PaperPositionRepository positionRepository;
    private final PaperAccountRepository accountRepository;
    private final FyersService fyersService;
    private final BroadcastService broadcastService;

    @Transactional
    public PaperOrder placeOrder(Long userId, PlaceOrderRequest request) {
        String clientOrderId = request.getClientOrderId() != null ? request.getClientOrderId() : "PAPER-" + UUID.randomUUID();
        PaperOrder order = PaperOrder.builder()
                .userId(userId)
                .orderId("PAPER-" + UUID.randomUUID())
                .clientOrderId(clientOrderId)
                .symbol(request.getSymbol())
                .side(request.getSide().name())
                .orderType(request.getOrderType().name())
                .quantity(request.getQty())
                .price(request.getPrice())
                .status(STATUS_OPEN)
                .createdAt(LocalDateTime.now())
                .build();
        orderRepository.save(order);

        if (shouldFillImmediately(request)) {
            fillOrder(order, request);
        }
        broadcastService.broadcastOrders(userId, orderRepository.findByUserId(userId));
        return order;
    }

    @Transactional
    public PaperOrder modifyOrder(Long userId, Long orderId, OrderModifyRequest request) {
        PaperOrder order = orderRepository.findById(orderId)
                .filter(found -> found.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (!STATUS_OPEN.equalsIgnoreCase(order.getStatus())) {
            throw new ConflictException("Only open orders can be modified");
        }
        if (request.getQty() != null) {
            order.setQuantity(request.getQty());
        }
        if (request.getPrice() != null) {
            order.setPrice(request.getPrice());
        }
        if (request.getOrderType() != null) {
            order.setOrderType(request.getOrderType().name());
        }
        orderRepository.save(order);
        broadcastService.broadcastOrders(userId, orderRepository.findByUserId(userId));
        return order;
    }

    @Transactional
    public PaperOrder cancelOrder(Long userId, Long orderId) {
        PaperOrder order = orderRepository.findById(orderId)
                .filter(found -> found.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (!STATUS_OPEN.equalsIgnoreCase(order.getStatus())) {
            throw new ConflictException("Only open orders can be cancelled");
        }
        order.setStatus(STATUS_CANCELLED);
        orderRepository.save(order);
        broadcastService.broadcastOrders(userId, orderRepository.findByUserId(userId));
        return order;
    }

    @Transactional
    public PaperOrder closePosition(Long userId, String symbol, int qty) {
        List<PaperPosition> openPositions = positionRepository.findByUserIdAndStatus(userId, STATUS_OPEN);
        Optional<PaperPosition> positionOpt = openPositions.stream()
                .filter(position -> position.getSymbol().equalsIgnoreCase(symbol))
                .findFirst();
        PaperPosition position = positionOpt.orElseThrow(() -> new NotFoundException("No open position for symbol"));
        int closeQty = qty > 0 ? Math.min(qty, position.getQuantity()) : position.getQuantity();
        if (closeQty <= 0) {
            throw new BadRequestException("Quantity must be greater than zero");
        }
        BigDecimal exitPrice = MoneyUtils.bd(fyersService.getLTP(symbol));
        BigDecimal pnl = calculatePnl(position.getSide(), position.getAveragePrice(), exitPrice, closeQty);
        position.setQuantity(position.getQuantity() - closeQty);
        position.setLastPrice(exitPrice);
        position.setUnrealizedPnl(MoneyUtils.ZERO);
        if (position.getQuantity() == 0) {
            position.setStatus("CLOSED");
            position.setExitTime(LocalDateTime.now());
        }
        positionRepository.save(position);

        PaperTrade trade = PaperTrade.builder()
                .userId(userId)
                .symbol(symbol)
                .side(position.getSide())
                .quantity(closeQty)
                .entryPrice(position.getAveragePrice())
                .entryTime(position.getEntryTime())
                .exitPrice(exitPrice)
                .exitTime(LocalDateTime.now())
                .realizedPnl(pnl)
                .status("CLOSED")
                .build();
        tradeRepository.save(trade);
        updateAccountForExit(userId, pnl, exitPrice, closeQty, position.getSide());
        broadcastService.broadcastPositions(userId, positionRepository.findByUserIdAndStatus(userId, STATUS_OPEN));
        PaperOrder order = PaperOrder.builder()
                .userId(userId)
                .orderId("PAPER-" + UUID.randomUUID())
                .clientOrderId("CLOSE-" + UUID.randomUUID())
                .symbol(symbol)
                .side(position.getSide().equalsIgnoreCase("BUY") ? "SELL" : "BUY")
                .orderType("MARKET")
                .quantity(closeQty)
                .price(exitPrice)
                .status(STATUS_FILLED)
                .createdAt(LocalDateTime.now())
                .build();
        return orderRepository.save(order);
    }

    private boolean shouldFillImmediately(PlaceOrderRequest request) {
        return request.getOrderType() == PlaceOrderRequest.OrderType.MARKET
                || request.getOrderType() == PlaceOrderRequest.OrderType.SL_M;
    }

    private void fillOrder(PaperOrder order, PlaceOrderRequest request) {
        BigDecimal fillPrice = resolveFillPrice(order, request);
        order.setStatus(STATUS_FILLED);
        order.setPrice(fillPrice);
        orderRepository.save(order);

        PaperPosition position = positionRepository.findByUserIdAndStatus(order.getUserId(), STATUS_OPEN).stream()
                .filter(existing -> existing.getSymbol().equalsIgnoreCase(order.getSymbol()))
                .findFirst()
                .orElse(null);

        if (position == null) {
            position = PaperPosition.builder()
                    .userId(order.getUserId())
                    .symbol(order.getSymbol())
                    .side(order.getSide())
                    .quantity(order.getQuantity())
                    .averagePrice(fillPrice)
                    .lastPrice(fillPrice)
                    .unrealizedPnl(MoneyUtils.ZERO)
                    .status(STATUS_OPEN)
                    .entryTime(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        } else {
            int newQty = position.getQuantity() + order.getQuantity();
            BigDecimal newAvg = position.getAveragePrice()
                    .multiply(BigDecimal.valueOf(position.getQuantity()))
                    .add(fillPrice.multiply(BigDecimal.valueOf(order.getQuantity())))
                    .divide(BigDecimal.valueOf(newQty), MoneyUtils.SCALE, java.math.RoundingMode.HALF_UP);
            position.setQuantity(newQty);
            position.setAveragePrice(newAvg);
            position.setLastPrice(fillPrice);
        }
        positionRepository.save(position);

        PaperTrade trade = PaperTrade.builder()
                .userId(order.getUserId())
                .symbol(order.getSymbol())
                .side(order.getSide())
                .quantity(order.getQuantity())
                .entryPrice(fillPrice)
                .entryTime(LocalDateTime.now())
                .status(STATUS_OPEN)
                .build();
        tradeRepository.save(trade);

        updateAccountForEntry(order.getUserId(), fillPrice, order.getQuantity(), order.getSide());
        broadcastService.broadcastPositions(order.getUserId(), positionRepository.findByUserIdAndStatus(order.getUserId(), STATUS_OPEN));
    }

    private BigDecimal resolveFillPrice(PaperOrder order, PlaceOrderRequest request) {
        if (request.getOrderType() == PlaceOrderRequest.OrderType.LIMIT && request.getPrice() != null) {
            BigDecimal ltp = MoneyUtils.bd(fyersService.getLTP(order.getSymbol()));
            if (order.getSide().equalsIgnoreCase("BUY") && ltp.compareTo(request.getPrice()) <= 0) {
                return request.getPrice();
            }
            if (order.getSide().equalsIgnoreCase("SELL") && ltp.compareTo(request.getPrice()) >= 0) {
                return request.getPrice();
            }
            throw new ConflictException("Limit order not filled at current price");
        }
        if (request.getOrderType() == PlaceOrderRequest.OrderType.SL && request.getTriggerPrice() != null) {
            BigDecimal ltp = MoneyUtils.bd(fyersService.getLTP(order.getSymbol()));
            boolean triggered = order.getSide().equalsIgnoreCase("BUY")
                    ? ltp.compareTo(request.getTriggerPrice()) >= 0
                    : ltp.compareTo(request.getTriggerPrice()) <= 0;
            if (!triggered) {
                throw new ConflictException("Stop-loss order not triggered");
            }
            return request.getPrice() != null ? request.getPrice() : ltp;
        }
        return MoneyUtils.bd(fyersService.getLTP(order.getSymbol()));
    }

    private void updateAccountForEntry(Long userId, BigDecimal price, int qty, String side) {
        PaperAccount account = accountRepository.findByUserId(userId)
                .orElseGet(() -> accountRepository.save(PaperAccount.builder()
                        .userId(userId)
                        .startingCapital(MoneyUtils.ZERO)
                        .cashBalance(MoneyUtils.ZERO)
                        .reservedMargin(MoneyUtils.ZERO)
                        .realizedPnl(MoneyUtils.ZERO)
                        .unrealizedPnl(MoneyUtils.ZERO)
                        .updatedAt(LocalDateTime.now())
                        .build()));
        BigDecimal notional = price.multiply(BigDecimal.valueOf(qty));
        if (side.equalsIgnoreCase("BUY")) {
            if (account.getCashBalance().compareTo(notional) < 0) {
                throw new ConflictException("Insufficient cash balance");
            }
            account.setCashBalance(MoneyUtils.subtract(account.getCashBalance(), notional));
        } else {
            account.setCashBalance(MoneyUtils.add(account.getCashBalance(), notional));
        }
        account.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(account);
    }

    private void updateAccountForExit(Long userId, BigDecimal pnl, BigDecimal price, int qty, String side) {
        PaperAccount account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Paper account not found"));
        BigDecimal notional = price.multiply(BigDecimal.valueOf(qty));
        if (side.equalsIgnoreCase("BUY")) {
            account.setCashBalance(MoneyUtils.add(account.getCashBalance(), notional));
        } else {
            account.setCashBalance(MoneyUtils.subtract(account.getCashBalance(), notional));
        }
        account.setRealizedPnl(MoneyUtils.add(account.getRealizedPnl(), pnl));
        account.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(account);
    }

    private BigDecimal calculatePnl(String side, BigDecimal entry, BigDecimal exit, int qty) {
        BigDecimal diff = exit.subtract(entry);
        if (side.equalsIgnoreCase("SELL")) {
            diff = entry.subtract(exit);
        }
        return diff.multiply(BigDecimal.valueOf(qty));
    }
}
