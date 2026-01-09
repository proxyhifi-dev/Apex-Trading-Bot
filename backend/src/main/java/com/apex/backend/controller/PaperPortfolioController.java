package com.apex.backend.controller;

import com.apex.backend.dto.PaperAccountAmountRequest;
import com.apex.backend.dto.PaperAccountDTO;
import com.apex.backend.dto.PaperAccountResetRequest;
import com.apex.backend.dto.PaperPositionDTO;
import com.apex.backend.dto.PaperStatsDTO;
import com.apex.backend.dto.PaperSummaryDTO;
import com.apex.backend.model.PaperAccount;
import com.apex.backend.model.PaperPortfolioStats;
import com.apex.backend.model.PaperPosition;
import com.apex.backend.security.JwtTokenProvider;
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
    private final JwtTokenProvider jwtTokenProvider;
    
    /**
     * Get open paper trading positions
     */
    @GetMapping("/positions/open")
    public ResponseEntity<?> getOpenPositions(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            log.info("Fetching open paper positions");
            Long userId = resolveUserId(authHeader);
            List<PaperPositionDTO> positions = paperTradingService.getOpenPositions(userId).stream()
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
    public ResponseEntity<?> getClosedPositions(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            log.info("Fetching closed paper positions");
            Long userId = resolveUserId(authHeader);
            List<PaperPositionDTO> positions = paperTradingService.getClosedPositions(userId).stream()
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
    public ResponseEntity<?> getStats(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            log.info("Fetching paper trading stats");
            Long userId = resolveUserId(authHeader);
            PaperPortfolioStats storedStats = paperTradingService.getStats(userId);
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

    /**
     * Get paper trading summary (cash/used/free/pnl/positions)
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Long userId = resolveUserId(authHeader);
            List<PaperPosition> openPositions = paperTradingService.getOpenPositions(userId);
            PaperAccount account = paperTradingService.getAccount(userId);
            double pnl = account.getRealizedPnl() + account.getUnrealizedPnl();
            double cash = account.getCashBalance();
            double used = account.getReservedMargin();
            double free = cash;
            PaperSummaryDTO summary = PaperSummaryDTO.builder()
                    .cash(cash)
                    .used(used)
                    .free(free)
                    .pnl(pnl)
                    .positions(openPositions.size())
                    .build();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Failed to fetch paper summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch paper summary"));
        }
    }

    @GetMapping("/account")
    public ResponseEntity<?> getAccount(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Long userId = resolveUserId(authHeader);
            PaperAccount account = paperTradingService.getAccount(userId);
            return ResponseEntity.ok(toAccountDto(account));
        } catch (Exception e) {
            log.error("Failed to fetch paper account", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch paper account"));
        }
    }

    @PostMapping("/account/reset")
    public ResponseEntity<?> resetAccount(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                          @RequestBody PaperAccountResetRequest request) {
        try {
            Long userId = resolveUserId(authHeader);
            double startingCapital = request.getStartingCapital() != null ? request.getStartingCapital() : 0.0;
            PaperAccount account = paperTradingService.resetAccount(userId, startingCapital);
            return ResponseEntity.ok(toAccountDto(account));
        } catch (Exception e) {
            log.error("Failed to reset paper account", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to reset paper account"));
        }
    }

    @PostMapping("/account/deposit")
    public ResponseEntity<?> deposit(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                     @RequestBody PaperAccountAmountRequest request) {
        try {
            Long userId = resolveUserId(authHeader);
            double amount = request.getAmount() != null ? request.getAmount() : 0.0;
            PaperAccount account = paperTradingService.deposit(userId, amount);
            return ResponseEntity.ok(toAccountDto(account));
        } catch (Exception e) {
            log.error("Failed to deposit to paper account", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to deposit to paper account"));
        }
    }

    @PostMapping("/account/withdraw")
    public ResponseEntity<?> withdraw(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                      @RequestBody PaperAccountAmountRequest request) {
        try {
            Long userId = resolveUserId(authHeader);
            double amount = request.getAmount() != null ? request.getAmount() : 0.0;
            PaperAccount account = paperTradingService.withdraw(userId, amount);
            return ResponseEntity.ok(toAccountDto(account));
        } catch (Exception e) {
            log.error("Failed to withdraw from paper account", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to withdraw from paper account"));
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

    private PaperAccountDTO toAccountDto(PaperAccount account) {
        return PaperAccountDTO.builder()
                .startingCapital(account.getStartingCapital())
                .cashBalance(account.getCashBalance())
                .reservedMargin(account.getReservedMargin())
                .realizedPnl(account.getRealizedPnl())
                .unrealizedPnl(account.getUnrealizedPnl())
                .updatedAt(account.getUpdatedAt())
                .build();
    }

    private Long resolveUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalStateException("Missing Authorization header");
        }
        String jwt = authHeader.substring(7);
        Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
        if (userId == null) {
            throw new IllegalStateException("Invalid user token");
        }
        return userId;
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
