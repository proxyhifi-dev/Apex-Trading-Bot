package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.dto.ScanDiagnosticsReason;
import com.apex.backend.dto.ScanRequest;
import com.apex.backend.dto.ScanResponse;
import com.apex.backend.dto.ScanSignalResponse;
import com.apex.backend.dto.ScanPipelineStats;
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
import org.slf4j.MDC;
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
    public void executeRun(Long runId, Long userId, String correlationId, ScannerRunRequest request) {
        MDC.put("runId", String.valueOf(runId));
        MDC.put("userId", String.valueOf(userId));
        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }
        log.info("EXECUTOR START: Processing runId={} for userId={}", runId, userId);
        try {
            ScannerRun run = scannerRunRepository.findById(runId)
                    .orElseThrow(() -> new NotFoundException("Scan run not found"));

            if (run.getStatus() == ScannerRun.Status.CANCELLED) {
                log.info("Run {} was cancelled before execution started.", runId);
                return;
            }

            run.setStatus(ScannerRun.Status.RUNNING);
            run.setStartedAt(Instant.now());
            scannerRunRepository.save(run);

            if (!strategyConfig.getScanner().isEnabled()) {
                markRunFailed(run, "Scanner configuration is disabled.");
                return;
            }

            List<String> symbols = resolveSymbols(userId, request);
            int symbolCount = symbols != null ? symbols.size() : 0;
            log.info("Resolved {} symbols for scan run {} (userId={})", symbolCount, runId, userId);

            if (symbols == null || symbols.isEmpty()) {
                applyEmptyUniverseDiagnostics(run);
                completeRun(run);
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

            run = scannerRunRepository.findById(runId).orElse(run);
            if (run.getStatus() == ScannerRun.Status.CANCELLED) {
                log.info("Run {} was cancelled during execution.", runId);
                return;
            }

            updateRunWithResponse(run, response);
            saveResults(run, response.getSignals());
            completeRun(run);

            log.info("✅ Completed scan run {} for user {}", runId, userId);

        } catch (Exception ex) {
            log.error("❌ Scanner run {} failed for user {}", runId, userId, ex);
            scannerRunRepository.findById(runId).ifPresent(r -> markRunFailed(r, ex.getMessage()));
        } finally {
            log.info("EXECUTOR STOP: Completed processing runId={} for userId={}", runId, userId);
            MDC.clear();
        }
    }

    private void completeRun(ScannerRun run) {
        run.setStatus(ScannerRun.Status.COMPLETED);
        run.setCompletedAt(Instant.now());
        scannerRunRepository.save(run);
    }

    private void updateRunWithResponse(ScannerRun run, ScanResponse response) {
        if (response == null || response.getDiagnostics() == null) {
            applyZeroDiagnostics(run);
            return;
        }
        var diagnostics = response.getDiagnostics();
        run.setTotalSymbols(defaultCount(diagnostics.getTotalSymbols()));
        run.setPassedStage1(defaultCount(diagnostics.getPassedStage1()));
        run.setPassedStage2(defaultCount(diagnostics.getPassedStage2()));
        run.setFinalSignals(defaultCount(diagnostics.getFinalSignals()));
        run.setStagePassCounts(serialize(defaultStagePassCounts(response.getPipeline())));
        run.setRejectedStage1ReasonCounts(serialize(defaultReasonMap(diagnostics.getRejectedStage1ReasonCounts())));
        run.setRejectedStage2ReasonCounts(serialize(defaultReasonMap(diagnostics.getRejectedStage2ReasonCounts())));
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
            case WATCHLIST -> watchlistService.resolveSymbolsForStrategyOrDefault(userId, request.getStrategyId());
            case SYMBOLS -> watchlistService.normalizeSymbols(request.getSymbols());
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
            return "{}";
        }
    }

    private void applyEmptyUniverseDiagnostics(ScannerRun run) {
        run.setTotalSymbols(0);
        run.setPassedStage1(0);
        run.setPassedStage2(0);
        run.setFinalSignals(0);
        run.setStagePassCounts(serialize(defaultStagePassCounts(null)));
        run.setRejectedStage1ReasonCounts(serialize(java.util.Map.of(ScanDiagnosticsReason.EMPTY_UNIVERSE.name(), 1L)));
        run.setRejectedStage2ReasonCounts(serialize(java.util.Map.of()));
    }

    private void applyZeroDiagnostics(ScannerRun run) {
        run.setTotalSymbols(0);
        run.setPassedStage1(0);
        run.setPassedStage2(0);
        run.setFinalSignals(0);
        run.setStagePassCounts(serialize(defaultStagePassCounts(null)));
        run.setRejectedStage1ReasonCounts(serialize(java.util.Map.of()));
        run.setRejectedStage2ReasonCounts(serialize(java.util.Map.of()));
    }

    private int defaultCount(Integer value) {
        return value == null ? 0 : value;
    }

    private java.util.Map<String, Long> defaultReasonMap(java.util.Map<String, Long> reasons) {
        return reasons == null ? java.util.Map.of() : reasons;
    }

    private java.util.Map<String, Long> defaultStagePassCounts(ScanPipelineStats pipelineStats) {
        if (pipelineStats == null) {
            return java.util.Map.of(
                    "trend", 0L,
                    "volume", 0L,
                    "breakout", 0L,
                    "rsi", 0L,
                    "adx", 0L,
                    "atr", 0L,
                    "momentum", 0L,
                    "squeeze", 0L,
                    "finalSignals", 0L
            );
        }
        return java.util.Map.of(
                "trend", (long) pipelineStats.getTrendPassed(),
                "volume", (long) pipelineStats.getVolumePassed(),
                "breakout", (long) pipelineStats.getBreakoutPassed(),
                "rsi", (long) pipelineStats.getRsiPassed(),
                "adx", (long) pipelineStats.getAdxPassed(),
                "atr", (long) pipelineStats.getAtrPassed(),
                "momentum", (long) pipelineStats.getMomentumPassed(),
                "squeeze", (long) pipelineStats.getSqueezePassed(),
                "finalSignals", (long) pipelineStats.getFinalSignals()
        );
    }

    private void markRunFailed(ScannerRun run, String message) {
        if (run.getStartedAt() == null) {
            run.setStartedAt(Instant.now());
        }
        run.setStatus(ScannerRun.Status.FAILED);
        run.setErrorMessage(message != null ? message : "Scanner run failed");
        if (run.getTotalSymbols() == null) {
            run.setTotalSymbols(0);
        }
        if (run.getPassedStage1() == null) {
            run.setPassedStage1(0);
        }
        if (run.getPassedStage2() == null) {
            run.setPassedStage2(0);
        }
        if (run.getFinalSignals() == null) {
            run.setFinalSignals(0);
        }
        if (run.getStagePassCounts() == null) {
            run.setStagePassCounts(serialize(defaultStagePassCounts(null)));
        }
        if (run.getRejectedStage1ReasonCounts() == null) {
            run.setRejectedStage1ReasonCounts(serialize(java.util.Map.of()));
        }
        if (run.getRejectedStage2ReasonCounts() == null) {
            run.setRejectedStage2ReasonCounts(serialize(java.util.Map.of()));
        }
        run.setCompletedAt(Instant.now());
        scannerRunRepository.save(run);
    }
}
