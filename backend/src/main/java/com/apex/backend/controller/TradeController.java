package com.apex.backend.controller;

import com.apex.backend.dto.PerformanceMetrics;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:4200")
public class TradeController {

    private final TradeRepository tradeRepository;

    @GetMapping
    public List<Trade> getAllTrades() {
        log.info("📊 Fetching all trades");
        return tradeRepository.findAll();
    }

    @GetMapping("/recent")
    public List<Trade> getRecentTrades(@RequestParam(defaultValue = "10") int limit) {
        log.info("📊 Fetching recent {} trades", limit);
        List<Trade> allTrades = tradeRepository.findAll();
        int startIndex = Math.max(0, allTrades.size() - limit);
        return allTrades.subList(startIndex, allTrades.size());
    }

    @GetMapping("/stats")
    public Map<String, Object> getTradeStats() {
        log.info("📊 Calculating trade stats");
        List<Trade> allTrades = tradeRepository.findAll();

        if (allTrades.isEmpty()) {
            return Map.of(
                    "totalTrades", 0,
                    "winningTrades", 0,
                    "losingTrades", 0,
                    "winRate", 0.0,
                    "profitFactor", 0.0,
                    "totalProfit", 0.0,
                    "totalLoss", 0.0,
                    "bestTrade", 0.0,
                    "worstTrade", 0.0
            );
        }

        long winCount = allTrades.stream().filter(t -> t.getPnl() != null && t.getPnl() > 0).count();
        long lossCount = allTrades.stream().filter(t -> t.getPnl() != null && t.getPnl() < 0).count();

        double totalProfit = allTrades.stream()
                .filter(t -> t.getPnl() != null && t.getPnl() > 0)
                .mapToDouble(Trade::getPnl)
                .sum();

        double totalLoss = Math.abs(allTrades.stream()
                .filter(t -> t.getPnl() != null && t.getPnl() < 0)
                .mapToDouble(Trade::getPnl)
                .sum());

        double profitFactor = totalLoss > 0 ? totalProfit / totalLoss : 0.0;
        double winRate = allTrades.size() > 0 ? (double) winCount / allTrades.size() * 100 : 0.0;

        double bestTrade = allTrades.stream()
                .filter(t -> t.getPnl() != null)
                .mapToDouble(Trade::getPnl)
                .max()
                .orElse(0.0);

        double worstTrade = allTrades.stream()
                .filter(t -> t.getPnl() != null)
                .mapToDouble(Trade::getPnl)
                .min()
                .orElse(0.0);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTrades", allTrades.size());
        stats.put("winningTrades", (int) winCount);
        stats.put("losingTrades", (int) lossCount);
        stats.put("winRate", Double.parseDouble(String.format("%.2f", winRate)));
        stats.put("profitFactor", Double.parseDouble(String.format("%.2f", profitFactor)));
        stats.put("totalProfit", Double.parseDouble(String.format("%.2f", totalProfit)));
        stats.put("totalLoss", Double.parseDouble(String.format("%.2f", totalLoss)));
        stats.put("bestTrade", Double.parseDouble(String.format("%.2f", bestTrade)));
        stats.put("worstTrade", Double.parseDouble(String.format("%.2f", worstTrade)));

        return stats;
    }

    @GetMapping("/all")
    public List<Trade> getAllTradesHistory() {
        log.info("📊 Fetching complete trade history");
        return tradeRepository.findAll();
    }
}
