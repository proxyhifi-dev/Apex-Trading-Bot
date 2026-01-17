package com.apex.backend.controller;

import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.PaperTradeRepository;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@Tag(name = "Exports")
public class ExportController {

    private final TradeRepository tradeRepository;
    private final PaperTradeRepository paperTradeRepository;
    private final StockScreeningResultRepository screeningRepository;
    private final SettingsService settingsService;

    @GetMapping(value = "/trades.csv", produces = "text/csv")
    @Operation(summary = "Export trades CSV")
    public ResponseEntity<byte[]> exportTrades(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        boolean isPaper = settingsService.isPaperModeForUser(userId);
        List<Trade> trades = isPaper
                ? paperTradeRepository.findByUserId(userId).stream()
                .map(paperTrade -> Trade.builder()
                        .id(paperTrade.getId())
                        .userId(userId)
                        .symbol(paperTrade.getSymbol())
                        .quantity(paperTrade.getQuantity())
                        .entryPrice(paperTrade.getEntryPrice())
                        .exitPrice(paperTrade.getExitPrice())
                        .entryTime(paperTrade.getEntryTime())
                        .exitTime(paperTrade.getExitTime())
                        .realizedPnl(paperTrade.getRealizedPnl())
                        .status(Trade.TradeStatus.valueOf(paperTrade.getStatus()))
                        .isPaperTrade(true)
                        .build())
                .toList()
                : tradeRepository.findByUserIdAndIsPaperTrade(userId, false);
        StringBuilder csv = new StringBuilder("id,symbol,quantity,entryPrice,exitPrice,entryTime,exitTime,realizedPnl,status\n");
        for (Trade trade : trades) {
            csv.append(trade.getId()).append(',')
                    .append(trade.getSymbol()).append(',')
                    .append(trade.getQuantity()).append(',')
                    .append(trade.getEntryPrice()).append(',')
                    .append(trade.getExitPrice()).append(',')
                    .append(trade.getEntryTime()).append(',')
                    .append(trade.getExitTime()).append(',')
                    .append(trade.getRealizedPnl()).append(',')
                    .append(trade.getStatus()).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=trades.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping(value = "/signals.csv", produces = "text/csv")
    @Operation(summary = "Export signals CSV")
    public ResponseEntity<byte[]> exportSignals(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        List<StockScreeningResult> signals = screeningRepository.findTop50ByUserIdOrderByScanTimeDesc(userId);
        StringBuilder csv = new StringBuilder("id,symbol,score,grade,entryPrice,scanTime,approvalStatus\n");
        for (StockScreeningResult signal : signals) {
            csv.append(signal.getId()).append(',')
                    .append(signal.getSymbol()).append(',')
                    .append(signal.getSignalScore()).append(',')
                    .append(signal.getGrade()).append(',')
                    .append(signal.getEntryPrice()).append(',')
                    .append(signal.getScanTime()).append(',')
                    .append(signal.getApprovalStatus()).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=signals.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }
}
