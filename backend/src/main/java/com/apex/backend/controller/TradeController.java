package com.apex.backend.controller;

import com.apex.backend.dto.PerformanceMetrics;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class TradeController {

    private final TradeRepository tradeRepository;

    @GetMapping("/history")
    public List<Trade> getTradeHistory() {
        return tradeRepository.findAll();
    }

    @GetMapping("/performance")
    public PerformanceMetrics getPerformance() {
        List<Trade> allTrades = tradeRepository.findAll();

        // ✅ FIXED: Using getRealizedPnl()
        long winCount = allTrades.stream()
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl() > 0)
                .count();

        long lossCount = allTrades.stream()
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl() < 0)
                .count();

        double totalPnl = allTrades.stream()
                .filter(t -> t.getRealizedPnl() != null)
                .mapToDouble(Trade::getRealizedPnl)
                .sum();

        double grossWin = allTrades.stream()
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl() > 0)
                .mapToDouble(Trade::getRealizedPnl)
                .sum();

        double grossLoss = Math.abs(allTrades.stream()
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl() < 0)
                .mapToDouble(Trade::getRealizedPnl)
                .sum());

        double profitFactor = grossLoss == 0 ? grossWin : grossWin / grossLoss;

        return PerformanceMetrics.builder()
                .totalTrades(allTrades.size())
                .winRate(allTrades.isEmpty() ? 0 : (double) winCount / allTrades.size() * 100)
                .netProfit(totalPnl)
                .profitFactor(profitFactor)
                .maxDrawdown(0.0) // Placeholder
                .build();
    }
}