package com.apex.backend.service;

import com.apex.backend.dto.PerformanceMetrics;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PerformanceService {

    private final TradeRepository tradeRepository;

    public PerformanceMetrics calculateMetrics() {
        List<Trade> allTrades = tradeRepository.findAll();
        List<Trade> closedTrades = allTrades.stream()
                .filter(t -> t.getStatus() == Trade.TradeStatus.CLOSED && t.getExitPrice() != null)
                .toList();

        if (closedTrades.isEmpty()) {
            return PerformanceMetrics.builder()
                    .totalTrades(0)
                    .build();
        }

        int totalTrades = closedTrades.size();

        List<Double> pnlList = closedTrades.stream()
                .map(this::calculatePnL)
                .toList();

        long winCount = pnlList.stream().filter(p -> p > 0).count();
        long lossCount = pnlList.stream().filter(p -> p < 0).count();

        double totalPnL = pnlList.stream().mapToDouble(Double::doubleValue).sum();
        double totalWins = pnlList.stream().filter(p -> p > 0).mapToDouble(Double::doubleValue).sum();
        double totalLosses = Math.abs(pnlList.stream().filter(p -> p < 0).mapToDouble(Double::doubleValue).sum());

        double winRate = totalTrades > 0 ? (double) winCount / totalTrades * 100 : 0;
        double avgWin = winCount > 0 ? totalWins / winCount : 0;
        double avgLoss = lossCount > 0 ? totalLosses / lossCount : 0;
        double profitFactor = totalLosses > 0 ? totalWins / totalLosses : 0;
        double expectancy = totalTrades > 0 ? totalPnL / totalTrades : 0;

        Trade lastTrade = closedTrades.get(closedTrades.size() - 1);

        return PerformanceMetrics.builder()
                .totalTrades(totalTrades)
                .winningTrades((int) winCount)
                .losingTrades((int) lossCount)
                .winRate(winRate)
                .totalProfitLoss(totalPnL)
                .averageWin(avgWin)
                .averageLoss(avgLoss)
                .profitFactor(profitFactor)
                .expectancy(expectancy)
                .maxDrawdown(0.0)
                .longestWinStreak(0)
                .longestLossStreak(0)
                .lastTradeTime(lastTrade.getExitTime())
                .lastTradeSymbol(lastTrade.getSymbol())
                .build();
    }

    private double calculatePnL(Trade trade) {
        return (trade.getExitPrice() - trade.getEntryPrice()) * trade.getQuantity();
    }
}
