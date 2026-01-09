package com.apex.backend.service;

import com.apex.backend.model.PaperAccount;
import com.apex.backend.model.PaperOrder;
import com.apex.backend.model.PaperPortfolioStats;
import com.apex.backend.model.PaperPosition;
import com.apex.backend.model.PaperTrade;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.PaperAccountRepository;
import com.apex.backend.repository.PaperOrderRepository;
import com.apex.backend.repository.PaperPortfolioStatsRepository;
import com.apex.backend.repository.PaperPositionRepository;
import com.apex.backend.repository.PaperTradeRepository;
import com.apex.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaperTradingService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_CLOSED = "CLOSED";

    private final PaperOrderRepository orderRepository;
    private final PaperTradeRepository tradeRepository;
    private final PaperPositionRepository positionRepository;
    private final PaperPortfolioStatsRepository statsRepository;
    private final PaperAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final FyersService fyersService;
    private final BroadcastService broadcastService;

    @Transactional
    public void recordEntry(Trade trade) {
        Long userId = resolveDefaultUserId();
        if (userId == null) {
            log.warn("Skipping paper entry record because no user is available.");
            return;
        }
        recordEntry(trade, userId);
    }

    @Transactional
    public void recordEntry(Trade trade, Long userId) {
        String orderId = "PAPER-" + UUID.randomUUID();
        PaperOrder order = PaperOrder.builder()
                .userId(userId)
                .orderId(orderId)
                .symbol(trade.getSymbol())
                .side(trade.getTradeType() == Trade.TradeType.SHORT ? "SELL" : "BUY")
                .orderType("MARKET")
                .quantity(trade.getQuantity())
                .price(trade.getEntryPrice())
                .status("FILLED")
                .createdAt(LocalDateTime.now())
                .build();
        orderRepository.save(order);
        broadcastService.broadcastOrders(orderRepository.findByUserId(userId));

        PaperTrade paperTrade = PaperTrade.builder()
                .userId(userId)
                .symbol(trade.getSymbol())
                .side(order.getSide())
                .quantity(trade.getQuantity())
                .entryPrice(trade.getEntryPrice())
                .entryTime(trade.getEntryTime())
                .status(STATUS_OPEN)
                .build();
        tradeRepository.save(paperTrade);

        PaperPosition position = PaperPosition.builder()
                .userId(userId)
                .symbol(trade.getSymbol())
                .side(order.getSide())
                .quantity(trade.getQuantity())
                .averagePrice(trade.getEntryPrice())
                .lastPrice(trade.getEntryPrice())
                .unrealizedPnl(0.0)
                .status(STATUS_OPEN)
                .entryTime(trade.getEntryTime())
                .build();
        positionRepository.save(position);
        broadcastService.broadcastPositions(positionRepository.findByUserIdAndStatus(userId, STATUS_OPEN));
        updateAccountForEntry(userId, trade);
    }

    @Transactional
    public void recordExit(Trade trade) {
        Long userId = resolveDefaultUserId();
        if (userId == null) {
            log.warn("Skipping paper exit record because no user is available.");
            return;
        }
        recordExit(trade, userId);
    }

    @Transactional
    public void recordExit(Trade trade, Long userId) {
        List<PaperPosition> openPositions = positionRepository.findByUserIdAndStatus(userId, STATUS_OPEN).stream()
                .filter(position -> position.getSymbol().equalsIgnoreCase(trade.getSymbol()))
                .collect(Collectors.toList());

        openPositions.forEach(position -> {
            position.setStatus(STATUS_CLOSED);
            position.setExitTime(trade.getExitTime());
            position.setLastPrice(trade.getExitPrice());
            double pnl = calculatePnl(trade.getTradeType(), trade.getEntryPrice(), trade.getExitPrice(), trade.getQuantity());
            position.setUnrealizedPnl(pnl);
        });
        positionRepository.saveAll(openPositions);

        List<PaperTrade> openTrades = tradeRepository.findByUserIdAndStatus(userId, STATUS_OPEN).stream()
                .filter(paperTrade -> paperTrade.getSymbol().equalsIgnoreCase(trade.getSymbol()))
                .collect(Collectors.toList());

        openTrades.forEach(paperTrade -> {
            paperTrade.setStatus(STATUS_CLOSED);
            paperTrade.setExitTime(trade.getExitTime());
            paperTrade.setExitPrice(trade.getExitPrice());
            paperTrade.setRealizedPnl(trade.getRealizedPnl());
        });
        tradeRepository.saveAll(openTrades);

        updateAccountForExit(userId, trade);
        updateStats(userId);
    }

    public List<PaperPosition> getOpenPositions(Long userId) {
        List<PaperPosition> positions = positionRepository.findByUserIdAndStatus(userId, STATUS_OPEN);
        refreshPositionsWithMarketData(positions);
        updateAccountUnrealized(userId, positions);
        return positions;
    }

    public List<PaperPosition> getClosedPositions(Long userId) {
        return positionRepository.findByUserIdAndStatus(userId, STATUS_CLOSED);
    }

    public List<PaperOrder> getOrders(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    public List<PaperTrade> getTrades(Long userId) {
        return tradeRepository.findByUserId(userId);
    }

    public PaperPortfolioStats getStats(Long userId) {
        return statsRepository.findTopByUserIdOrderByUpdatedAtDesc(userId)
                .orElseGet(() -> PaperPortfolioStats.builder()
                        .userId(userId)
                        .totalTrades(0)
                        .winningTrades(0)
                        .losingTrades(0)
                        .winRate(0.0)
                        .netPnl(0.0)
                        .updatedAt(LocalDateTime.now())
                        .build());
    }

    public PaperAccount getAccount(Long userId) {
        return accountRepository.findByUserId(userId)
                .orElseGet(() -> accountRepository.save(PaperAccount.builder()
                        .userId(userId)
                        .startingCapital(0.0)
                        .cashBalance(0.0)
                        .reservedMargin(0.0)
                        .realizedPnl(0.0)
                        .unrealizedPnl(0.0)
                        .updatedAt(LocalDateTime.now())
                        .build()));
    }

    @Transactional
    public PaperAccount resetAccount(Long userId, double startingCapital) {
        orderRepository.deleteByUserId(userId);
        tradeRepository.deleteByUserId(userId);
        positionRepository.deleteByUserId(userId);
        statsRepository.deleteByUserId(userId);
        accountRepository.deleteByUserId(userId);
        PaperAccount account = PaperAccount.builder()
                .userId(userId)
                .startingCapital(startingCapital)
                .cashBalance(startingCapital)
                .reservedMargin(0.0)
                .realizedPnl(0.0)
                .unrealizedPnl(0.0)
                .updatedAt(LocalDateTime.now())
                .build();
        return accountRepository.save(account);
    }

    @Transactional
    public PaperAccount deposit(Long userId, double amount) {
        PaperAccount account = getAccount(userId);
        account.setCashBalance(account.getCashBalance() + amount);
        return accountRepository.save(account);
    }

    @Transactional
    public PaperAccount withdraw(Long userId, double amount) {
        PaperAccount account = getAccount(userId);
        account.setCashBalance(account.getCashBalance() - amount);
        return accountRepository.save(account);
    }

    private void refreshPositionsWithMarketData(List<PaperPosition> positions) {
        if (positions.isEmpty()) {
            return;
        }
        List<String> symbols = positions.stream().map(PaperPosition::getSymbol).distinct().toList();
        Map<String, Double> ltpMap = fyersService.getLtpBatch(symbols);
        positions.forEach(position -> {
            Double ltp = ltpMap.get(position.getSymbol());
            if (ltp != null && ltp > 0) {
                position.setLastPrice(ltp);
                double pnl = calculatePnl(
                        "SELL".equalsIgnoreCase(position.getSide()) ? Trade.TradeType.SHORT : Trade.TradeType.LONG,
                        position.getAveragePrice(),
                        ltp,
                        position.getQuantity()
                );
                position.setUnrealizedPnl(pnl);
            }
        });
        positionRepository.saveAll(positions);
        broadcastService.broadcastPositions(positions);
    }

    private void updateStats(Long userId) {
        List<PaperTrade> trades = tradeRepository.findByUserId(userId);
        int totalTrades = trades.size();
        long winning = trades.stream().filter(trade -> trade.getRealizedPnl() != null && trade.getRealizedPnl() > 0).count();
        long losing = trades.stream().filter(trade -> trade.getRealizedPnl() != null && trade.getRealizedPnl() < 0).count();
        double netPnl = trades.stream().filter(trade -> trade.getRealizedPnl() != null).mapToDouble(PaperTrade::getRealizedPnl).sum();
        double winRate = totalTrades == 0 ? 0.0 : (winning / (double) totalTrades) * 100;

        PaperPortfolioStats stats = PaperPortfolioStats.builder()
                .userId(userId)
                .totalTrades(totalTrades)
                .winningTrades((int) winning)
                .losingTrades((int) losing)
                .winRate(winRate)
                .netPnl(netPnl)
                .updatedAt(LocalDateTime.now())
                .build();
        statsRepository.save(stats);
    }

    private void updateAccountForEntry(Long userId, Trade trade) {
        PaperAccount account = getAccount(userId);
        double required = trade.getEntryPrice() * trade.getQuantity();
        account.setCashBalance(account.getCashBalance() - required);
        account.setReservedMargin(account.getReservedMargin() + required);
        accountRepository.save(account);
    }

    private void updateAccountForExit(Long userId, Trade trade) {
        PaperAccount account = getAccount(userId);
        double entryCost = trade.getEntryPrice() * trade.getQuantity();
        double exitValue = trade.getExitPrice() != null ? trade.getExitPrice() * trade.getQuantity() : 0.0;
        account.setReservedMargin(account.getReservedMargin() - entryCost);
        account.setCashBalance(account.getCashBalance() + exitValue);
        if (trade.getRealizedPnl() != null) {
            account.setRealizedPnl(account.getRealizedPnl() + trade.getRealizedPnl());
        }
        accountRepository.save(account);
    }

    private void updateAccountUnrealized(Long userId, List<PaperPosition> positions) {
        if (positions == null) {
            return;
        }
        PaperAccount account = getAccount(userId);
        double unrealized = positions.stream()
                .map(PaperPosition::getUnrealizedPnl)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        account.setUnrealizedPnl(unrealized);
        accountRepository.save(account);
    }

    private Long resolveDefaultUserId() {
        return userRepository.findTopByOrderByIdAsc().map(user -> user.getId()).orElse(null);
    }

    private double calculatePnl(Trade.TradeType type, double entry, double exit, int qty) {
        double pnl = (exit - entry) * qty;
        if (type == Trade.TradeType.SHORT) {
            pnl = -pnl;
        }
        return pnl;
    }
}
