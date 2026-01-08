package com.apex.backend.controller;

import com.apex.backend.dto.PaperPositionDTO;
import com.apex.backend.dto.PaperStatsDTO;
import com.apex.backend.model.PaperPortfolioStats;
import com.apex.backend.model.PaperPosition;
import com.apex.backend.service.PaperTradingService;
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
public class PaperPortfolioController {
    
    private final PaperTradingService paperTradingService;
    
    /**
     * Get open paper trading positions
     */
    @GetMapping("/positions/open")
    public ResponseEntity<?> getOpenPositions() {
        try {
            log.info("Fetching open paper positions");
            List<PaperPositionDTO> positions = paperTradingService.getOpenPositions().stream()
                    .map(this::toPositionDto)
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
            List<PaperPositionDTO> positions = paperTradingService.getClosedPositions().stream()
                    .map(this::toPositionDto)
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
            PaperPortfolioStats storedStats = paperTradingService.getStats();
            PaperStatsDTO stats = PaperStatsDTO.builder()
                    .totalTrades(storedStats.getTotalTrades())
                    .winRate(storedStats.getWinRate())
                    .netPnl(storedStats.getNetPnl())
                    .build();
            
            log.info("Paper stats retrieved: totalTrades={}, netPnl={}", stats.getTotalTrades(), stats.getNetPnl());
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to fetch paper stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch paper stats"));
        }
    }

    private PaperPositionDTO toPositionDto(PaperPosition position) {
        double ltp = position.getLastPrice() != null ? position.getLastPrice() : position.getAveragePrice();
        double pnl = position.getUnrealizedPnl() != null ? position.getUnrealizedPnl() : 0.0;
        double pnlPercent = position.getAveragePrice() != null && position.getAveragePrice() != 0
                ? (pnl / (position.getAveragePrice() * position.getQuantity())) * 100
                : 0;
        return PaperPositionDTO.builder()
                .symbol(position.getSymbol())
                .quantity(position.getQuantity())
                .entryPrice(position.getAveragePrice())
                .ltp(ltp)
                .pnl(pnl)
                .pnlPercent(pnlPercent)
                .build();
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
