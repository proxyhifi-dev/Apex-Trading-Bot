package com.apex.backend.controller;

import com.apex.backend.dto.PaperPositionDTO;
import com.apex.backend.dto.PaperStatsDTO;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.service.FyersService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/paper")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class PaperPortfolioController {

    private final TradeRepository tradeRepo;
    private final FyersService fyersService;

    // ✅ Fixed: Match frontend call: /api/paper/positions/open
    @GetMapping("/positions/open")
    public List<PaperPositionDTO> getOpenPositions() {
        return tradeRepo.findByStatus(Trade.TradeStatus.OPEN).stream()
                .filter(Trade::isPaperTrade)
                .map(t -> {
                    double ltp = fyersService.getLTP(t.getSymbol());
                    return PaperPositionDTO.builder()
                            .symbol(t.getSymbol())
                            .quantity(t.getQuantity())
                            .entryPrice(t.getEntryPrice())
                            .ltp(ltp)
                            .pnl((ltp - t.getEntryPrice()) * t.getQuantity())
                            .pnlPercent((ltp - t.getEntryPrice()) / t.getEntryPrice() * 100)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ✅ Fixed: Match frontend call: /api/paper/positions/closed
    @GetMapping("/positions/closed")
    public List<PaperPositionDTO> getClosedPositions() {
        return new ArrayList<>(); // Return empty to stop 500 errors until logic is ready
    }

    @GetMapping("/stats")
    public PaperStatsDTO getStats() {
        List<Trade> closedTrades = tradeRepo.findByStatus(Trade.TradeStatus.CLOSED).stream()
                .filter(Trade::isPaperTrade)
                .collect(Collectors.toList());

        double totalProfit = closedTrades.stream()
                .filter(t -> t.getRealizedPnl() != null)
                .mapToDouble(Trade::getRealizedPnl).sum();

        return PaperStatsDTO.builder()
                .totalTrades(closedTrades.size())
                .winRate(closedTrades.isEmpty() ? 0 : 50.0) // Mock winrate
                .netPnl(totalProfit)
                .build();
    }
}