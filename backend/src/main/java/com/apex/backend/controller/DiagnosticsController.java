package com.apex.backend.controller;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.dto.DiagnosticsResponse;
import com.apex.backend.model.BotState;
import com.apex.backend.model.ScannerRun;
import com.apex.backend.repository.ScannerRunRepository;
import com.apex.backend.service.BotOpsService;
import com.apex.backend.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/diagnostics")
@RequiredArgsConstructor
@Slf4j
public class DiagnosticsController {

    private final StrategyConfig strategyConfig;
    private final WatchlistService watchlistService;
    private final ScannerRunRepository scannerRunRepository;
    private final BotOpsService botOpsService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${fyers.api.app-id:}")
    private String fyersAppId;

    @Value("${fyers.api.secret-key:}")
    private String fyersSecretKey;

    @GetMapping
    public ResponseEntity<DiagnosticsResponse> getDiagnostics() {
        Long ownerUserId = strategyConfig.getTrading().getOwnerUserId();
        int activeWatchlistCount = resolveActiveWatchlistCount(ownerUserId);
        Optional<ScannerRun> lastRun = scannerRunRepository.findTopByOrderByIdDesc();
        DiagnosticsResponse response = DiagnosticsResponse.builder()
                .scannerEnabled(strategyConfig.getScanner().isEnabled())
                .activeWatchlistStocks(activeWatchlistCount)
                .lastScannerRun(lastRun.map(this::toLastScannerRun).orElse(null))
                .botState(toBotStateSummary(ownerUserId))
                .fyersConfigured(isFyersConfigured())
                .databaseReachable(isDatabaseReachable())
                .build();
        return ResponseEntity.ok(response);
    }

    private int resolveActiveWatchlistCount(Long ownerUserId) {
        if (ownerUserId == null) {
            return 0;
        }
        Long strategyId = watchlistService.resolveDefaultStrategyId();
        List<String> symbols;
        if (strategyId != null) {
            symbols = watchlistService.resolveSymbolsForStrategyOrDefault(ownerUserId, strategyId);
        } else {
            symbols = watchlistService.resolveSymbolsForUser(ownerUserId);
        }
        return symbols != null ? symbols.size() : 0;
    }

    private DiagnosticsResponse.LastScannerRun toLastScannerRun(ScannerRun run) {
        return DiagnosticsResponse.LastScannerRun.builder()
                .id(run.getId())
                .status(run.getStatus() != null ? run.getStatus().name() : null)
                .error(run.getErrorMessage())
                .build();
    }

    private DiagnosticsResponse.BotStateSummary toBotStateSummary(Long ownerUserId) {
        if (ownerUserId == null) {
            return DiagnosticsResponse.BotStateSummary.builder()
                    .running(false)
                    .threadAlive(false)
                    .lastError("Owner user not configured")
                    .build();
        }
        BotState state = botOpsService.getState(ownerUserId);
        return DiagnosticsResponse.BotStateSummary.builder()
                .running(state.isRunning())
                .threadAlive(state.getThreadAlive())
                .lastError(state.getLastError())
                .build();
    }

    private boolean isFyersConfigured() {
        return fyersAppId != null && !fyersAppId.isBlank()
                && fyersSecretKey != null && !fyersSecretKey.isBlank();
    }

    private boolean isDatabaseReachable() {
        try {
            Integer result = jdbcTemplate.queryForObject("select 1", Integer.class);
            return result != null && result == 1;
        } catch (Exception ex) {
            log.warn("Diagnostics DB check failed: {}", ex.getMessage());
            return false;
        }
    }
}
