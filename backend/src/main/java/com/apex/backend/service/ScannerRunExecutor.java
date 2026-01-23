package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.dto.ScanRequest;
import com.apex.backend.dto.ScanResponse;
import com.apex.backend.dto.ScanSignalResponse;
import com.apex.backend.dto.ScannerRunRequest;
import com.apex.backend.exception.BadRequestException;
import com.apex.backend.exception.NotFoundException;
import com.apex.backend.model.ScannerRun;
import com.apex.backend.model.ScannerRunResult;
import com.apex.backend.repository.ScannerRunRepository;
import com.apex.backend.repository.ScannerRunResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScannerRunExecutor {

    private final ManualScanService manualScanService;
    private final WatchlistService watchlistService;
    private final StrategyConfig strategyConfig;
    private final ScannerRunRepository scannerRunRepository;
    private final ScannerRunResultRepository scannerRunResultRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeRun(Long runId, Long userId, ScannerRunRequest request) {
        try {
            ScannerRun run = scannerRunRepository.findById(runId)
                    .orElseThrow(() -> new NotFoundException("Scan run not found"));

            if (run.getStatus() == ScannerRun.Status.CANCELLED) {
                return;
            }

            run.setStatus(ScannerRun.Status.RUNNING);
            run.setStartedAt(Instant.now());
            scannerRunRepository.save(run);

            List<String> symbols = resolveSymbols(userId, request);

            if (symbols == null || symbols.isEmpty()) {
                run.setTotalSymbols(0);
                run.setPassedStage1(0);
                run.setPassedStage2(0);
                run.setFinalSignals(0);
                run.setRejectedStage1ReasonCounts(serialize(java.util.Map.of()));
                run.setRejectedStage2ReasonCounts(serialize(java.util.Map.of()));
                run.setStatus(ScannerRun.Status.COMPLETED);
                run.setCompletedAt(Instant.now());
                scannerRunRepository.save(run);
                return;
            }

            ScanRequest scanRequest = ScanRequest.builder()
                    .universe(ScanRequest.Universe.CUSTOM)
                    .symbols(symbols)
                    .tf(resolveTimeframe(request))
                    .regime(resolveRegime(request))
                    .dryRun(request.isDryRun())
                    .build();

            ScanResponse response = manualScanService.runManualScan(userId, scanRequest);

            if (scannerRunRepository.findById(runId).map(ScannerRun::getStatus).orElse(ScannerRun.Status.RUNNING)
                    == ScannerRun.Status.CANCELLED) {
                return;
            }

            updateRunWithResponse(run, response);
            saveResults(run, response.getSignals());

            run.setStatus(ScannerRun.Status.COMPLETED);
            run.setCompletedAt(Instant.now());
            scannerRunRepository.save(run);
        } catch (Exception ex) {
            log.error("Scanner run {} failed for user {}", runId, userId, ex);
            scannerRunRepository.findById(runId).ifPresent(r -> {
                if (r.getStartedAt() == null) {
                    r.setStartedAt(Instant.now());
                }
                r.setStatus(ScannerRun.Status.FAILED);
                r.setErrorMessage(ex.getMessage());
                r.setCompletedAt(Instant.now());
                scannerRunRepository.save(r);
            });
        }
    }

    private void updateRunWithResponse(ScannerRun run, ScanResponse response) {
        if (response == null || response.getDiagnostics() == null) {
            return;
        }
        var diagnostics = response.getDiagnostics();
        run.setTotalSymbols(diagnostics.getTotalSymbols());
        run.setPassedStage1(diagnostics.getPassedStage1());
        run.setPassedStage2(diagnostics.getPassedStage2());
        run.setFinalSignals(diagnostics.getFinalSignals());
        run.setRejectedStage1ReasonCounts(serialize(diagnostics.getRejectedStage1ReasonCounts()));
        run.setRejectedStage2ReasonCounts(serialize(diagnostics.getRejectedStage2ReasonCounts()));
    }

    private void saveResults(ScannerRun run, List<ScanSignalResponse> signals) {
        if (signals == null || signals.isEmpty()) {
            return;
        }
        List<ScannerRunResult> results = signals.stream()
                .map(signal -> ScannerRunResult.builder()
                        .run(run)
                        .symbol(signal.getSymbol())
                        .score(signal.getScore())
                        .grade(signal.getGrade())
                        .entryPrice(signal.getEntryPrice())
                        .reason(signal.getReason())
                        .createdAt(Instant.now())
                        .build())
                .toList();
        scannerRunResultRepository.saveAll(results);
    }

    private List<String> resolveSymbols(Long userId, ScannerRunRequest request) {
        return switch (request.getUniverseType()) {
            case WATCHLIST -> watchlistService.resolveSymbolsForUser(userId)
                    .stream()
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
            case SYMBOLS -> request.getSymbols();
            case INDEX -> resolveIndexSymbols(request);
        };
    }

    private List<String> resolveIndexSymbols(ScannerRunRequest request) {
        String index = request.getIndex();
        if (index == null || index.isBlank()) {
            throw new BadRequestException("Index name is required for INDEX universe");
        }
        String normalized = index.trim().toUpperCase(Locale.ROOT);
        List<String> symbols = switch (normalized) {
            case "NIFTY50" -> strategyConfig.getScanner().getUniverses().getNifty50();
            case "NIFTY200" -> strategyConfig.getScanner().getUniverses().getNifty200();
            default -> throw new BadRequestException("Unsupported index universe: " + index);
        };
        if (symbols == null || symbols.isEmpty()) {
            throw new BadRequestException("Index universe is empty or not configured");
        }
        return symbols;
    }

    private String resolveTimeframe(ScannerRunRequest request) {
        if (request.getTimeframe() != null && !request.getTimeframe().isBlank()) {
            return request.getTimeframe();
        }
        return strategyConfig.getScanner().getDefaultTimeframe();
    }

    private ScanRequest.Regime resolveRegime(ScannerRunRequest request) {
        if (request.getRegime() == null || request.getRegime().isBlank()) {
            return ScanRequest.Regime.valueOf(strategyConfig.getScanner().getDefaultRegime().toUpperCase(Locale.ROOT));
        }
        return ScanRequest.Regime.valueOf(request.getRegime().toUpperCase(Locale.ROOT));
    }

    private String serialize(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize scanner payload: {}", e.getMessage());
            return null;
        }
    }
}
