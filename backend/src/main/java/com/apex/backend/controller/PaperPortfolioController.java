package com.apex.backend.controller;

import com.apex.backend.dto.PaperPositionDTO;
import com.apex.backend.dto.PaperStatsDTO;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.service.FyersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/paper")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class PaperPortfolioController {
    
    private final TradeRepository tradeRepo;
    private final FyersService fyersService;
    
    /**
     * Get open paper trading positions
     */
    @GetMapping("/positions/open")
    public ResponseEntity<?> getOpenPositions() {
        try {
            log.info("Fetching open paper positions");
            List<PaperPositionDTO> positions = tradeRepo.findByStatus(Trade.TradeStatus.OPEN).stream()
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
            log.info("Retrieved {} open positions", positions.size());
            return ResponseEntity.ok(positions);
        } catch (Exception e) {
            log.error("Failed to fetch open positions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch open positions"));
        }
    }
    
    /**
     * Get closed paper trading positions
     */
    @GetMapping("/positions/closed")
    public ResponseEntity<?> getClosedPositions() {
        try {
            log.info("Fetching closed paper positions");
            List<PaperPositionDTO> positions = tradeRepo.findByStatus(Trade.TradeStatus.CLOSED).stream()
                    .filter(Trade::isPaperTrade)
                    .map(t -> {
                        double exitPrice = t.getExitPrice() != null ? t.getExitPrice() : t.getEntryPrice();
                        double pnl = t.getRealizedPnl() != null
                                ? t.getRealizedPnl()
                                : (exitPrice - t.getEntryPrice()) * t.getQuantity();
                        double pnlPercent = t.getEntryPrice() != null && t.getEntryPrice() != 0
                                ? (pnl / (t.getEntryPrice() * t.getQuantity())) * 100
                                : 0;
                        return PaperPositionDTO.builder()
                                .symbol(t.getSymbol())
                                .quantity(t.getQuantity())
                                .entryPrice(t.getEntryPrice())
                                .ltp(exitPrice)
                                .pnl(pnl)
                                .pnlPercent(pnlPercent)
                                .build();
                    })
                    .collect(Collectors.toList());
            log.info("Retrieved {} closed positions", positions.size());
            return ResponseEntity.ok(positions);
        } catch (Exception e) {
            log.error("Failed to fetch closed positions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch closed positions"));
        }
    }
    
    /**
     * Get paper trading statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        try {
            log.info("Fetching paper trading stats");
            List<Trade> closedTrades = tradeRepo.findByStatus(Trade.TradeStatus.CLOSED).stream()
                    .filter(Trade::isPaperTrade)
                    .collect(Collectors.toList());
            
            double totalProfit = closedTrades.stream()
                    .filter(t -> t.getRealizedPnl() != null)
                    .mapToDouble(Trade::getRealizedPnl)
                    .sum();

            long winningTrades = closedTrades.stream()
                    .mapToDouble(t -> t.getRealizedPnl() != null
                            ? t.getRealizedPnl()
                            : ((t.getExitPrice() != null ? t.getExitPrice() : t.getEntryPrice())
                            - t.getEntryPrice()) * t.getQuantity())
                    .filter(pnl -> pnl > 0)
                    .count();
            double winRate = closedTrades.isEmpty()
                    ? 0
                    : (winningTrades / (double) closedTrades.size()) * 100;
            
            PaperStatsDTO stats = PaperStatsDTO.builder()
                    .totalTrades(closedTrades.size())
                    .winRate(winRate)
                    .netPnl(totalProfit)
                    .build();
            
            log.info("Paper stats retrieved: totalTrades={}, netPnl={}", stats.getTotalTrades(), stats.getNetPnl());
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to fetch paper stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch paper stats"));
        }
    }
    
    // ==================== INNER CLASSES ====================
    
    public static class ErrorResponse {
        public String error;
        public long timestamp;
        
        public ErrorResponse(String error) {
            this.error = error;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
