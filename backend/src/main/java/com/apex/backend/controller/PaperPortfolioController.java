package com.apex.backend.controller;

import com.apex.backend.dto.PaperPositionDTO;
import com.apex.backend.dto.PaperStatsDTO;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.service.FyersService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/paper")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class PaperPortfolioController {

    private final TradeRepository tradeRepo;
    private final FyersService fyersService;

    @GetMapping("/positions")
    public List<PaperPositionDTO> getPositions() {
        return tradeRepo.findByStatus(Trade.TradeStatus.OPEN).stream()
                .filter(Trade::isPaperTrade)
                .map(t -> {
                    double ltp = fyersService.getLTP(t.getSymbol());
                    double pnl = (ltp - t.getEntryPrice()) * t.getQuantity();
                    double pnlPct = (ltp - t.getEntryPrice()) / t.getEntryPrice() * 100;

                    return PaperPositionDTO.builder()
                            .symbol(t.getSymbol())
                            .quantity(t.getQuantity())
                            .entryPrice(t.getEntryPrice())
                            .ltp(ltp)
                            .pnl(pnl)
                            .pnlPercent(pnlPct)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/stats")
    public PaperStatsDTO getStats() {
        List<Trade> closedTrades = tradeRepo.findByStatus(Trade.TradeStatus.CLOSED).stream()
                .filter(Trade::isPaperTrade)
                .collect(Collectors.toList());

        // ✅ FIXED: Using getRealizedPnl() instead of getPnl()
        double totalProfit = closedTrades.stream()
                .filter(t -> t.getRealizedPnl() != null)
                .mapToDouble(Trade::getRealizedPnl)
                .sum();

        long winCount = closedTrades.stream()
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl() > 0)
                .count();

        long lossCount = closedTrades.stream()
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl() < 0)
                .count();

        double winRate = closedTrades.isEmpty() ? 0 : (double) winCount / closedTrades.size() * 100;

        return PaperStatsDTO.builder()
                .totalTrades(closedTrades.size())
                .winRate(winRate)
                .netPnl(totalProfit)
                .profitFactor(calculateProfitFactor(closedTrades))
                .build();
    }

    private double calculateProfitFactor(List<Trade> trades) {
        // ✅ FIXED: Using getRealizedPnl()
        double grossWin = trades.stream()
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl() > 0)
                .mapToDouble(Trade::getRealizedPnl).sum();

        double grossLoss = Math.abs(trades.stream()
                .filter(t -> t.getRealizedPnl() != null && t.getRealizedPnl() < 0)
                .mapToDouble(Trade::getRealizedPnl).sum());

        return grossLoss == 0 ? grossWin : grossWin / grossLoss;
    }
}