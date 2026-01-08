package com.apex.backend.service;

import com.apex.backend.model.PaperOrder;
import com.apex.backend.model.PaperPortfolioStats;
import com.apex.backend.model.PaperPosition;
import com.apex.backend.model.PaperTrade;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.PaperOrderRepository;
import com.apex.backend.repository.PaperPortfolioStatsRepository;
import com.apex.backend.repository.PaperPositionRepository;
import com.apex.backend.repository.PaperTradeRepository;
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
    private final FyersService fyersService;
    private final BroadcastService broadcastService;

    @Transactional
    public void recordEntry(Trade trade) {
        String orderId = "PAPER-" + UUID.randomUUID();
        PaperOrder order = PaperOrder.builder()
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
        broadcastService.broadcastOrders(orderRepository.findAll());

        PaperTrade paperTrade = PaperTrade.builder()
                .symbol(trade.getSymbol())
                .side(order.getSide())
                .quantity(trade.getQuantity())
                .entryPrice(trade.getEntryPrice())
                .entryTime(trade.getEntryTime())
                .status(STATUS_OPEN)
                .build();
        tradeRepository.save(paperTrade);

        PaperPosition position = PaperPosition.builder()
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
        broadcastService.broadcastPositions(positionRepository.findByStatus(STATUS_OPEN));
    }

    @Transactional
    public void recordExit(Trade trade) {
        List<PaperPosition> openPositions = positionRepository.findByStatus(STATUS_OPEN).stream()
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

        List<PaperTrade> openTrades = tradeRepository.findByStatus(STATUS_OPEN).stream()
                .filter(paperTrade -> paperTrade.getSymbol().equalsIgnoreCase(trade.getSymbol()))
                .collect(Collectors.toList());

        openTrades.forEach(paperTrade -> {
            paperTrade.setStatus(STATUS_CLOSED);
            paperTrade.setExitTime(trade.getExitTime());
            paperTrade.setExitPrice(trade.getExitPrice());
            paperTrade.setRealizedPnl(trade.getRealizedPnl());
        });
        tradeRepository.saveAll(openTrades);

        updateStats();
    }

    public List<PaperPosition> getOpenPositions() {
        List<PaperPosition> positions = positionRepository.findByStatus(STATUS_OPEN);
        refreshPositionsWithMarketData(positions);
        return positions;
    }

    public List<PaperPosition> getClosedPositions() {
        return positionRepository.findByStatus(STATUS_CLOSED);
    }

    public List<PaperOrder> getOrders() {
        return orderRepository.findAll();
    }

    public List<PaperTrade> getTrades() {
        return tradeRepository.findAll();
    }

    public PaperPortfolioStats getStats() {
        return statsRepository.findAll().stream()
                .max(Comparator.comparing(PaperPortfolioStats::getUpdatedAt))
                .orElseGet(() -> PaperPortfolioStats.builder()
                        .totalTrades(0)
                        .winningTrades(0)
                        .losingTrades(0)
                        .winRate(0.0)
                        .netPnl(0.0)
                        .updatedAt(LocalDateTime.now())
                        .build());
    }

    public double getAvailableFunds(double initialCapital) {
        PaperPortfolioStats stats = getStats();
        return initialCapital + Optional.ofNullable(stats.getNetPnl()).orElse(0.0);
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

    private void updateStats() {
        List<PaperTrade> trades = tradeRepository.findAll();
        int totalTrades = trades.size();
        long winning = trades.stream().filter(trade -> trade.getRealizedPnl() != null && trade.getRealizedPnl() > 0).count();
        long losing = trades.stream().filter(trade -> trade.getRealizedPnl() != null && trade.getRealizedPnl() < 0).count();
        double netPnl = trades.stream().filter(trade -> trade.getRealizedPnl() != null).mapToDouble(PaperTrade::getRealizedPnl).sum();
        double winRate = totalTrades == 0 ? 0.0 : (winning / (double) totalTrades) * 100;

        PaperPortfolioStats stats = PaperPortfolioStats.builder()
                .totalTrades(totalTrades)
                .winningTrades((int) winning)
                .losingTrades((int) losing)
                .winRate(winRate)
                .netPnl(netPnl)
                .updatedAt(LocalDateTime.now())
                .build();
        statsRepository.save(stats);
    }

    private double calculatePnl(Trade.TradeType type, double entry, double exit, int qty) {
        double pnl = (exit - entry) * qty;
        if (type == Trade.TradeType.SHORT) {
            pnl = -pnl;
        }
        return pnl;
    }
}
