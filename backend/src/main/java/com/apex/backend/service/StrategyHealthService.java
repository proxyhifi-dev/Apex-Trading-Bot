package com.apex.backend.service;

import com.apex.backend.config.StrategyProperties;
import com.apex.backend.model.StrategyHealthState;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.StrategyHealthStateRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.trading.pipeline.StrategyHealthDecision;
import com.apex.backend.trading.pipeline.StrategyHealthEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StrategyHealthService implements StrategyHealthEngine {

    private final StrategyProperties strategyProperties;
    private final TradeRepository tradeRepository;
    private final StrategyHealthStateRepository strategyHealthStateRepository;

    @Override
    public StrategyHealthDecision evaluate(Long userId) {
        if (userId == null) {
            return new StrategyHealthDecision(StrategyHealthDecision.StrategyHealthStatus.WARNING, List.of("User scope missing"));
        }
        List<Trade> trades = tradeRepository.findTop50ByUserIdAndStatusOrderByExitTimeDesc(userId, Trade.TradeStatus.CLOSED);
        int rolling = strategyProperties.getHealth().getRollingTrades();
        List<Trade> slice = trades.size() > rolling ? trades.subList(0, rolling) : trades;

        List<String> reasons = new ArrayList<>();
        double expectancy = slice.stream()
                .filter(t -> t.getRealizedPnl() != null)
                .mapToDouble(t -> t.getRealizedPnl().doubleValue())
                .average()
                .orElse(0.0);
        if (expectancy < strategyProperties.getHealth().getMinExpectancy()) {
            reasons.add("Expectancy below threshold");
        }
        double sharpe = computeSharpe(slice);
        if (sharpe < strategyProperties.getHealth().getMinSharpe()) {
            reasons.add("Sharpe deterioration detected");
        }
        double drawdown = computeMaxDrawdown(slice);
        if (drawdown > strategyProperties.getHealth().getMaxDrawdownPct()) {
            reasons.add("Drawdown breach");
        }
        double lossProbability = consecutiveLossProbability(slice);
        if (lossProbability > strategyProperties.getHealth().getMaxConsecutiveLossProbability()) {
            reasons.add("Consecutive loss probability too high");
        }

        StrategyHealthDecision.StrategyHealthStatus status = reasons.isEmpty()
                ? StrategyHealthDecision.StrategyHealthStatus.HEALTHY
                : (reasons.size() > 2 ? StrategyHealthDecision.StrategyHealthStatus.BROKEN : StrategyHealthDecision.StrategyHealthStatus.WARNING);

        StrategyHealthState state = strategyHealthStateRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .orElseGet(() -> StrategyHealthState.builder()
                        .userId(userId)
                        .createdAt(LocalDateTime.now())
                        .paused(false)
                        .build());
        state.setStatus(status.name());
        state.setReasons(String.join("; ", reasons));
        state.setUpdatedAt(LocalDateTime.now());
        if (status == StrategyHealthDecision.StrategyHealthStatus.BROKEN) {
            state.setPaused(true);
        }
        strategyHealthStateRepository.save(state);

        return new StrategyHealthDecision(status, reasons);
    }

    public StrategyHealthState pause(Long userId, String reason) {
        StrategyHealthState state = strategyHealthStateRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .orElseGet(() -> StrategyHealthState.builder()
                        .userId(userId)
                        .createdAt(LocalDateTime.now())
                        .build());
        state.setPaused(true);
        state.setStatus(StrategyHealthDecision.StrategyHealthStatus.BROKEN.name());
        state.setReasons(reason);
        state.setUpdatedAt(LocalDateTime.now());
        return strategyHealthStateRepository.save(state);
    }

    public StrategyHealthState resume(Long userId) {
        StrategyHealthState state = strategyHealthStateRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .orElseGet(() -> StrategyHealthState.builder()
                        .userId(userId)
                        .createdAt(LocalDateTime.now())
                        .build());
        state.setPaused(false);
        state.setStatus(StrategyHealthDecision.StrategyHealthStatus.HEALTHY.name());
        state.setReasons("Manual resume");
        state.setUpdatedAt(LocalDateTime.now());
        return strategyHealthStateRepository.save(state);
    }

    public StrategyHealthState getLatestState(Long userId) {
        return strategyHealthStateRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
    }

    private double computeSharpe(List<Trade> trades) {
        if (trades.isEmpty()) {
            return 0.0;
        }
        double mean = trades.stream()
                .filter(t -> t.getRealizedPnl() != null)
                .mapToDouble(t -> t.getRealizedPnl().doubleValue())
                .average()
                .orElse(0.0);
        double variance = trades.stream()
                .filter(t -> t.getRealizedPnl() != null)
                .mapToDouble(t -> Math.pow(t.getRealizedPnl().doubleValue() - mean, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);
        return stdDev == 0 ? 0.0 : mean / stdDev;
    }

    private double computeMaxDrawdown(List<Trade> trades) {
        double equity = 0.0;
        double peak = 0.0;
        double maxDrawdown = 0.0;
        for (int i = trades.size() - 1; i >= 0; i--) {
            Trade trade = trades.get(i);
            double pnl = trade.getRealizedPnl() == null ? 0.0 : trade.getRealizedPnl().doubleValue();
            equity += pnl;
            if (equity > peak) {
                peak = equity;
            }
            double drawdown = peak == 0 ? 0 : (peak - equity) / Math.abs(peak);
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
        }
        return maxDrawdown;
    }

    private double consecutiveLossProbability(List<Trade> trades) {
        if (trades.isEmpty()) {
            return 0.0;
        }
        int lossStreak = 0;
        for (Trade trade : trades) {
            if (trade.getRealizedPnl() != null && trade.getRealizedPnl().doubleValue() < 0) {
                lossStreak++;
            } else {
                break;
            }
        }
        double winRate = trades.stream().filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl().doubleValue() > 0).count() / (double) trades.size();
        double lossProb = 1.0 - winRate;
        return Math.pow(lossProb, Math.max(lossStreak, 1));
    }
}
