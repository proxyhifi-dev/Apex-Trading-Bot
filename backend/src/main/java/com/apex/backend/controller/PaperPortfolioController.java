package com.apex.backend.controller;

import com.apex.backend.dto.PaperAccountAmountRequest;
import com.apex.backend.dto.PaperAccountDTO;
import com.apex.backend.dto.PaperAccountResetRequest;
import com.apex.backend.dto.PaperPositionDTO;
import com.apex.backend.dto.PaperStatsDTO;
import com.apex.backend.dto.PaperSummaryDTO;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.model.PaperAccount;
import com.apex.backend.model.PaperPortfolioStats;
import com.apex.backend.model.PaperPosition;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.PaperTradingService;
import com.apex.backend.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
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
    public ResponseEntity<?> getOpenPositions(@AuthenticationPrincipal UserPrincipal principal) {
        log.info("Fetching open paper positions");
        Long userId = requireUserId(principal);
        List<PaperPositionDTO> positions = paperTradingService.getOpenPositions(userId).stream()
                .map(this::toPositionDto)
                .collect(Collectors.toList());
        log.info("Retrieved {} open positions", positions.size());
        return ResponseEntity.ok(positions);
    }
    
    /**
     * Get closed paper trading positions
     */
    @GetMapping("/positions/closed")
    public ResponseEntity<?> getClosedPositions(@AuthenticationPrincipal UserPrincipal principal) {
        log.info("Fetching closed paper positions");
        Long userId = requireUserId(principal);
        List<PaperPositionDTO> positions = paperTradingService.getClosedPositions(userId).stream()
                .map(this::toPositionDto)
                .collect(Collectors.toList());
        log.info("Retrieved {} closed positions", positions.size());
        return ResponseEntity.ok(positions);
    }
    
    /**
     * Get paper trading statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@AuthenticationPrincipal UserPrincipal principal) {
        log.info("Fetching paper trading stats");
        Long userId = requireUserId(principal);
        PaperPortfolioStats storedStats = paperTradingService.getStats(userId);
        PaperStatsDTO stats = PaperStatsDTO.builder()
                .totalTrades(storedStats.getTotalTrades())
                .winRate(storedStats.getWinRate())
                .netPnl(storedStats.getNetPnl())
                .build();

        log.info("Paper stats retrieved: totalTrades={}, netPnl={}", stats.getTotalTrades(), stats.getNetPnl());
        return ResponseEntity.ok(stats);
    }

    /**
     * Get paper trading summary (cash/used/free/pnl/positions)
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        List<PaperPosition> openPositions = paperTradingService.getOpenPositions(userId);
        PaperAccount account = paperTradingService.getAccount(userId);
        BigDecimal pnl = MoneyUtils.add(account.getRealizedPnl(), account.getUnrealizedPnl());
        BigDecimal cash = account.getCashBalance();
        BigDecimal used = account.getReservedMargin();
        BigDecimal free = cash;
        PaperSummaryDTO summary = PaperSummaryDTO.builder()
                .cash(cash)
                .used(used)
                .free(free)
                .pnl(pnl)
                .positions(openPositions.size())
                .build();
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/account")
    public ResponseEntity<?> getAccount(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        PaperAccount account = paperTradingService.getAccount(userId);
        return ResponseEntity.ok(toAccountDto(account));
    }

    @PostMapping("/account/reset")
    public ResponseEntity<?> resetAccount(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody(required = false) PaperAccountResetRequest request) {
        requireAdmin(principal);
        Long userId = requireUserId(principal);
        BigDecimal startingCapital = request != null ? request.getBalance() : null;
        if (startingCapital == null || startingCapital.compareTo(BigDecimal.ZERO) <= 0) {
            startingCapital = MoneyUtils.bd(100000.0);
        }
        PaperAccount account = paperTradingService.resetAccount(userId, startingCapital);
        return ResponseEntity.ok(toAccountDto(account));
    }

    @PostMapping("/account/deposit")
    public ResponseEntity<?> deposit(@AuthenticationPrincipal UserPrincipal principal,
                                     @RequestBody PaperAccountAmountRequest request) {
        Long userId = requireUserId(principal);
        BigDecimal amount = request.getAmount() != null ? request.getAmount() : MoneyUtils.ZERO;
        PaperAccount account = paperTradingService.deposit(userId, amount);
        return ResponseEntity.ok(toAccountDto(account));
    }

    @PostMapping("/account/withdraw")
    public ResponseEntity<?> withdraw(@AuthenticationPrincipal UserPrincipal principal,
                                      @RequestBody PaperAccountAmountRequest request) {
        Long userId = requireUserId(principal);
        BigDecimal amount = request.getAmount() != null ? request.getAmount() : MoneyUtils.ZERO;
        PaperAccount account = paperTradingService.withdraw(userId, amount);
        return ResponseEntity.ok(toAccountDto(account));
    }

    private PaperPositionDTO toPositionDto(PaperPosition position) {
        BigDecimal ltp = position.getLastPrice() != null ? position.getLastPrice() : position.getAveragePrice();
        BigDecimal pnl = position.getUnrealizedPnl() != null ? position.getUnrealizedPnl() : MoneyUtils.ZERO;
        BigDecimal pnlPercent = BigDecimal.ZERO;
        BigDecimal denominator = MoneyUtils.multiply(position.getAveragePrice(), position.getQuantity());
        if (denominator.compareTo(BigDecimal.ZERO) > 0) {
            pnlPercent = MoneyUtils.scale(pnl.divide(denominator, MoneyUtils.SCALE, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)));
        }
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

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }

    private void requireAdmin(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        if (principal.getRole() == null || !principal.getRole().equalsIgnoreCase("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
    }
}
