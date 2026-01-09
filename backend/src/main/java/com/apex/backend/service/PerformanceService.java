package com.apex.backend.service;

import com.apex.backend.model.Trade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class PerformanceService {

    @Value("${apex.trading.capital:100000}")
    private double initialCapital;

    /**
     * ✅ FIXED: Proper max drawdown calculation
     * Calculates the peak-to-trough decline
     */
    public double calculateMaxDrawdown(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0.0;
        }

        double maxDrawdown = 0.0;
        double peak = initialCapital;
        double runningBalance = initialCapital;

        for (Trade trade : trades) {
            if (trade.getRealizedPnl() != null) {
                runningBalance += trade.getRealizedPnl().doubleValue();

                // Update peak if we reach a new high
                if (runningBalance > peak) {
                    peak = runningBalance;
                }

                // Calculate drawdown from peak
                double currentDrawdown = ((peak - runningBalance) / peak) * 100;
                if (currentDrawdown > maxDrawdown) {
                    maxDrawdown = currentDrawdown;
                }
            }
        }

        log.debug("Max drawdown calculated: {}%", maxDrawdown);
        return Math.round(maxDrawdown * 100.0) / 100.0; // Round to 2 decimals
    }

    /**
     * Calculate win rate percentage
     */
    public double calculateWinRate(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0.0;
        }

        long winCount = trades.stream()
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl().compareTo(java.math.BigDecimal.ZERO) > 0)
                .count();

        return (double) winCount / trades.size() * 100;
    }

    /**
     * Calculate profit factor (Gross Wins / Gross Losses)
     */
    public double calculateProfitFactor(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0.0;
        }

        double grossWin = trades.stream()
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl().compareTo(java.math.BigDecimal.ZERO) > 0)
                .mapToDouble(t -> t.getRealizedPnl().doubleValue())
                .sum();

        double grossLoss = Math.abs(trades.stream()
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl().compareTo(java.math.BigDecimal.ZERO) < 0)
                .mapToDouble(t -> t.getRealizedPnl().doubleValue())
                .sum());

        if (grossLoss == 0) {
            return grossWin > 0 ? Double.POSITIVE_INFINITY : 0;
        }

        return grossWin / grossLoss;
    }

    /**
     * Calculate expectancy (average profit per trade)
     */
    public double calculateExpectancy(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0.0;
        }

        double totalPnl = trades.stream()
                .filter(t -> t.getRealizedPnl() != null)
                .mapToDouble(t -> t.getRealizedPnl().doubleValue())
                .sum();

        return totalPnl / trades.size();
    }

    /**
     * Calculate average win
     */
    public double calculateAverageWin(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0.0;
        }

        List<Trade> winningTrades = trades.stream()
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl().compareTo(java.math.BigDecimal.ZERO) > 0)
                .toList();

        if (winningTrades.isEmpty()) {
            return 0.0;
        }

        double totalWins = winningTrades.stream()
                .mapToDouble(t -> t.getRealizedPnl().doubleValue())
                .sum();

        return totalWins / winningTrades.size();
    }

    /**
     * Calculate average loss
     */
    public double calculateAverageLoss(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0.0;
        }

        List<Trade> losingTrades = trades.stream()
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl().compareTo(java.math.BigDecimal.ZERO) < 0)
                .toList();

        if (losingTrades.isEmpty()) {
            return 0.0;
        }

        double totalLosses = losingTrades.stream()
                .mapToDouble(t -> t.getRealizedPnl().doubleValue())
                .sum();

        return totalLosses / losingTrades.size();
    }

    /**
     * Calculate consecutive wins
     */
    public int calculateLongestWinStreak(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0;
        }

        int maxStreak = 0;
        int currentStreak = 0;

        for (Trade trade : trades) {
            if (trade.getRealizedPnl() != null && trade.getRealizedPnl().compareTo(java.math.BigDecimal.ZERO) > 0) {
                currentStreak++;
                maxStreak = Math.max(maxStreak, currentStreak);
            } else {
                currentStreak = 0;
            }
        }

        return maxStreak;
    }

    /**
     * Calculate consecutive losses
     */
    public int calculateLongestLossStreak(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0;
        }

        int maxStreak = 0;
        int currentStreak = 0;

        for (Trade trade : trades) {
            if (trade.getRealizedPnl() != null && trade.getRealizedPnl().compareTo(java.math.BigDecimal.ZERO) < 0) {
                currentStreak++;
                maxStreak = Math.max(maxStreak, currentStreak);
            } else {
                currentStreak = 0;
            }
        }

        return maxStreak;
    }

    /**
     * Calculate Sharpe Ratio (risk-adjusted return)
     * Assumes daily returns and risk-free rate of 0
     */
    public double calculateSharpeRatio(List<Trade> trades) {
        if (trades == null || trades.size() < 2) {
            return 0.0;
        }

        // Calculate average return
        double avgReturn = calculateExpectancy(trades);

        // Calculate standard deviation
        double sumSquaredDiff = 0.0;
        for (Trade trade : trades) {
            if (trade.getRealizedPnl() != null) {
                double diff = trade.getRealizedPnl().doubleValue() - avgReturn;
                sumSquaredDiff += diff * diff;
            }
        }

        double stdDev = Math.sqrt(sumSquaredDiff / trades.size());

        // Sharpe Ratio = (Return - RiskFreeRate) / StdDev
        if (stdDev == 0) {
            return 0.0;
        }

        return avgReturn / stdDev;
    }

    /**
     * Calculate return on investment (ROI)
     */
    public double calculateROI(double totalProfit) {
        if (initialCapital == 0) {
            return 0.0;
        }
        return (totalProfit / initialCapital) * 100;
    }
}
