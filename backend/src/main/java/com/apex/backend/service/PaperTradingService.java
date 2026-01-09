package com.apex.backend.service;

import com.apex.backend.exception.BadRequestException;
import com.apex.backend.exception.ConflictException;
import com.apex.backend.exception.NotFoundException;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.apex.backend.util.MoneyUtils;

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
    private final FyersService fyersService;
    private final BroadcastService broadcastService;

    @Transactional
    public void recordEntry(Long userId, Trade trade) {
        validateTrade(trade);
        PaperAccount account = getAccount(userId);
        BigDecimal required = MoneyUtils.multiply(trade.getEntryPrice(), trade.getQuantity());
        if (account.getCashBalance().compareTo(required) < 0) {
            throw new ConflictException("Insufficient cash balance for entry");
        }

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
                .unrealizedPnl(MoneyUtils.ZERO)
                .status(STATUS_OPEN)
                .entryTime(trade.getEntryTime())
                .build();
        positionRepository.save(position);
        broadcastService.broadcastPositions(positionRepository.findByUserIdAndStatus(userId, STATUS_OPEN));
        updateAccountForEntry(userId, trade);
    }

    @Transactional
    public void recordExit(Long userId, Trade trade) {
        validateTrade(trade);
        List<PaperPosition> openPositions = positionRepository.findByUserIdAndStatus(userId, STATUS_OPEN).stream()
                .filter(position -> position.getSymbol().equalsIgnoreCase(trade.getSymbol()))
                .collect(Collectors.toList());
        if (openPositions.isEmpty()) {
            throw new NotFoundException("No open position found for symbol: " + trade.getSymbol());
        }
        if (trade.getExitPrice() == null) {
            throw new BadRequestException("Exit price is required");
        }

        openPositions.forEach(position -> {
            position.setStatus(STATUS_CLOSED);
            position.setExitTime(trade.getExitTime());
            position.setLastPrice(trade.getExitPrice());
            BigDecimal pnl = calculatePnl(trade.getTradeType(), trade.getEntryPrice(), trade.getExitPrice(), trade.getQuantity());
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
                        .netPnl(MoneyUtils.ZERO)
                        .date(LocalDate.now())
                        .updatedAt(LocalDateTime.now())
                        .build());
    }

    public PaperAccount getAccount(Long userId) {
        return accountRepository.findByUserId(userId)
                .orElseGet(() -> accountRepository.save(PaperAccount.builder()
                        .userId(userId)
                        .startingCapital(MoneyUtils.ZERO)
                        .cashBalance(MoneyUtils.ZERO)
                        .reservedMargin(MoneyUtils.ZERO)
                        .realizedPnl(MoneyUtils.ZERO)
                        .unrealizedPnl(MoneyUtils.ZERO)
                        .updatedAt(LocalDateTime.now())
                        .build()));
    }

    @Transactional
    public PaperAccount resetAccount(Long userId, BigDecimal startingCapital) {
        if (startingCapital == null || startingCapital.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Starting capital must be greater than zero");
        }
        orderRepository.deleteByUserId(userId);
        tradeRepository.deleteByUserId(userId);
        positionRepository.deleteByUserId(userId);
        statsRepository.deleteByUserId(userId);
        accountRepository.deleteByUserId(userId);
        PaperAccount account = PaperAccount.builder()
                .userId(userId)
                .startingCapital(MoneyUtils.scale(startingCapital))
                .cashBalance(MoneyUtils.scale(startingCapital))
                .reservedMargin(MoneyUtils.ZERO)
                .realizedPnl(MoneyUtils.ZERO)
                .unrealizedPnl(MoneyUtils.ZERO)
                .updatedAt(LocalDateTime.now())
                .build();
        return accountRepository.save(account);
    }

    @Transactional
    public PaperAccount deposit(Long userId, BigDecimal amount) {
        validateAmount(amount, "Deposit amount must be greater than zero");
        PaperAccount account = getAccount(userId);
        account.setCashBalance(MoneyUtils.add(account.getCashBalance(), amount));
        return accountRepository.save(account);
    }

    @Transactional
    public PaperAccount withdraw(Long userId, BigDecimal amount) {
        validateAmount(amount, "Withdraw amount must be greater than zero");
        PaperAccount account = getAccount(userId);
        if (account.getCashBalance().compareTo(amount) < 0) {
            throw new ConflictException("Withdrawal exceeds available cash balance");
        }
        account.setCashBalance(MoneyUtils.subtract(account.getCashBalance(), amount));
        return accountRepository.save(account);
    }

    @Transactional
    public void refreshLtp(Long userId) {
        List<PaperPosition> positions = positionRepository.findByUserIdAndStatus(userId, STATUS_OPEN);
        refreshPositionsWithMarketData(positions);
        updateAccountUnrealized(userId, positions);
    }

    private void refreshPositionsWithMarketData(List<PaperPosition> positions) {
        if (positions.isEmpty()) {
            return;
        }
        List<String> symbols = positions.stream().map(PaperPosition::getSymbol).distinct().toList();
        Map<String, BigDecimal> ltpMap = fyersService.getLtpBatch(symbols);
        positions.forEach(position -> {
            BigDecimal ltp = ltpMap.get(position.getSymbol());
            if (ltp != null && ltp.compareTo(BigDecimal.ZERO) > 0) {
                position.setLastPrice(ltp);
                BigDecimal pnl = calculatePnl(
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
        long winning = trades.stream().filter(trade -> trade.getRealizedPnl() != null && trade.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0).count();
        long losing = trades.stream().filter(trade -> trade.getRealizedPnl() != null && trade.getRealizedPnl().compareTo(BigDecimal.ZERO) < 0).count();
        BigDecimal netPnl = trades.stream()
                .map(PaperTrade::getRealizedPnl)
                .filter(Objects::nonNull)
                .reduce(MoneyUtils.ZERO, MoneyUtils::add);
        double winRate = totalTrades == 0 ? 0.0 : (winning / (double) totalTrades) * 100;

        PaperPortfolioStats stats = PaperPortfolioStats.builder()
                .userId(userId)
                .totalTrades(totalTrades)
                .winningTrades((int) winning)
                .losingTrades((int) losing)
                .winRate(winRate)
                .netPnl(netPnl)
                .date(LocalDate.now())
                .updatedAt(LocalDateTime.now())
                .build();
        statsRepository.save(stats);
    }

    private void updateAccountForEntry(Long userId, Trade trade) {
        PaperAccount account = getAccount(userId);
        BigDecimal required = MoneyUtils.multiply(trade.getEntryPrice(), trade.getQuantity());
        account.setCashBalance(MoneyUtils.subtract(account.getCashBalance(), required));
        account.setReservedMargin(MoneyUtils.add(account.getReservedMargin(), required));
        accountRepository.save(account);
    }

    private void updateAccountForExit(Long userId, Trade trade) {
        PaperAccount account = getAccount(userId);
        BigDecimal entryCost = MoneyUtils.multiply(trade.getEntryPrice(), trade.getQuantity());
        BigDecimal exitValue = trade.getExitPrice() != null
                ? MoneyUtils.multiply(trade.getExitPrice(), trade.getQuantity())
                : MoneyUtils.ZERO;
        account.setReservedMargin(MoneyUtils.subtract(account.getReservedMargin(), entryCost));
        account.setCashBalance(MoneyUtils.add(account.getCashBalance(), exitValue));
        if (trade.getRealizedPnl() != null) {
            account.setRealizedPnl(MoneyUtils.add(account.getRealizedPnl(), trade.getRealizedPnl()));
        }
        accountRepository.save(account);
    }

    private void updateAccountUnrealized(Long userId, List<PaperPosition> positions) {
        if (positions == null) {
            return;
        }
        PaperAccount account = getAccount(userId);
        BigDecimal unrealized = positions.stream()
                .map(PaperPosition::getUnrealizedPnl)
                .filter(Objects::nonNull)
                .reduce(MoneyUtils.ZERO, MoneyUtils::add);
        account.setUnrealizedPnl(unrealized);
        accountRepository.save(account);
    }

    private BigDecimal calculatePnl(Trade.TradeType type, BigDecimal entry, BigDecimal exit, int qty) {
        BigDecimal pnl = MoneyUtils.multiply(MoneyUtils.subtract(exit, entry), qty);
        if (type == Trade.TradeType.SHORT) {
            pnl = pnl.negate();
        }
        return MoneyUtils.scale(pnl);
    }

    private void validateAmount(BigDecimal amount, String message) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(message);
        }
    }

    private void validateTrade(Trade trade) {
        if (trade == null || trade.getQuantity() == null || trade.getQuantity() <= 0) {
            throw new BadRequestException("Trade quantity must be greater than zero");
        }
        if (trade.getEntryPrice() == null || trade.getEntryPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Entry price must be greater than zero");
        }
    }
}
