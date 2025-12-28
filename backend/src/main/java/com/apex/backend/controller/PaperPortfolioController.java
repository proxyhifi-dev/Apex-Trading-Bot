package com.apex.backend.controller;

import com.apex.backend.dto.PaperPositionDTO;
import com.apex.backend.dto.PaperStatsDTO;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/paper")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:4200")
public class PaperPortfolioController {

    private final TradeRepository tradeRepo;

    // ✅ FIX: Added missing endpoint for Open Positions
    @GetMapping("/positions")
    public ResponseEntity<List<PaperPositionDTO>> getPaperPositions() {
        log.info("📊 Fetching open paper positions...");

        List<Trade> openTrades = tradeRepo.findByIsPaperTradeAndStatus(true, Trade.TradeStatus.OPEN);

        List<PaperPositionDTO> positions = openTrades.stream()
                .map(t -> PaperPositionDTO.builder()
                        .symbol(t.getSymbol())
                        .quantity(t.getQuantity())
                        .avgPrice(t.getEntryPrice())
                        // .ltp(...) // You could fetch live price here if needed
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(positions);
    }

    @GetMapping("/stats")
    public ResponseEntity<PaperStatsDTO> getPaperStats() {
        List<Trade> closedPaperTrades = tradeRepo.findByIsPaperTradeAndStatus(true, Trade.TradeStatus.CLOSED);

        double totalPnL = closedPaperTrades.stream()
                .filter(t -> t.getPnl() != null)
                .mapToDouble(Trade::getPnl)
                .sum();

        double totalProfit = closedPaperTrades.stream()
                .filter(t -> t.getPnl() != null && t.getPnl() > 0)
                .mapToDouble(Trade::getPnl)
                .sum();

        double totalLoss = closedPaperTrades.stream()
                .filter(t -> t.getPnl() != null && t.getPnl() < 0)
                .mapToDouble(Trade::getPnl)
                .sum();

        long winCount = closedPaperTrades.stream()
                .filter(t -> t.getPnl() != null && t.getPnl() > 0)
                .count();

        long lossCount = closedPaperTrades.stream()
                .filter(t -> t.getPnl() != null && t.getPnl() < 0)
                .count();

        double winRate = closedPaperTrades.isEmpty() ? 0.0 : (double) winCount / closedPaperTrades.size() * 100;

        PaperStatsDTO stats = PaperStatsDTO.builder()
                .totalTrades(closedPaperTrades.size())
                .winningTrades((int) winCount)
                .losingTrades((int) lossCount)
                .winRate(winRate)
                .totalProfit(totalProfit)
                .totalLoss(totalLoss)
                .netPnL(totalPnL)
                .build();

        return ResponseEntity.ok(stats);
    }
}