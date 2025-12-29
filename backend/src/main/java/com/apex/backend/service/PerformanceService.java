package com.apex.backend.service;

import com.apex.backend.dto.PerformanceMetrics;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final TradeRepository tradeRepo;

    public PerformanceMetrics calculateMetrics() {
        List<Trade> trades = tradeRepo.findAll();

        if (trades.isEmpty()) {
            return PerformanceMetrics.builder()
                    .totalTrades(0)
                    .netProfit(0.0)
                    .build();
        }

        int totalTrades = trades.size();
        long winningTrades = trades.stream().filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl() > 0).count();
        long losingTrades = trades.stream().filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl() <= 0).count();

        double totalPnL = trades.stream()
                .filter(t -> t.getRealizedPnl() != null)
                .mapToDouble(Trade::getRealizedPnl)
                .sum();

        double winRate = (double) winningTrades / totalTrades * 100;

        // Calculate Profit Factor
        double grossProfit = trades.stream()
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl() > 0)
                .mapToDouble(Trade::getRealizedPnl)
                .sum();

        double grossLoss = Math.abs(trades.stream()
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl() < 0)
                .mapToDouble(Trade::getRealizedPnl)
                .sum());

        double profitFactor = (grossLoss == 0) ? grossProfit : grossProfit / grossLoss;

        return PerformanceMetrics.builder()
                .totalTrades(totalTrades)
                .winningTrades((int) winningTrades)
                .losingTrades((int) losingTrades)
                .winRate(winRate)
                .netProfit(totalPnL) // ✅ FIXED: Was totalProfitLoss
                .profitFactor(profitFactor)
                .averageWin(winningTrades > 0 ? grossProfit / winningTrades : 0)
                .averageLoss(losingTrades > 0 ? grossLoss / losingTrades : 0)
                .maxDrawdown(calculateMaxDrawdown(trades))
                .build();
    }

    private double calculateMaxDrawdown(List<Trade> trades) {
        double maxDrawdown = 0.0;
        double peak = 0.0;
        double currentEquity = 100000.0; // Base assumption or fetch from config

        for (Trade t : trades) {
            if (t.getRealizedPnl() != null) {
                currentEquity += t.getRealizedPnl();
                if (currentEquity > peak) peak = currentEquity;
                double drawdown = (peak - currentEquity) / peak * 100;
                if (drawdown > maxDrawdown) maxDrawdown = drawdown;
            }
        }
        return maxDrawdown;
    }
}