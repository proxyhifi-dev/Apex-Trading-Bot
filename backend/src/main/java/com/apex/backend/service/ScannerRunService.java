package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.dto.ScanDiagnosticsBreakdown;
import com.apex.backend.dto.ScanRequest;
import com.apex.backend.dto.ScanResponse;
import com.apex.backend.dto.ScanSignalResponse;
import com.apex.backend.dto.ScannerRunRequest;
import com.apex.backend.dto.ScannerRunResponse;
import com.apex.backend.dto.ScannerRunResultResponse;
import com.apex.backend.dto.ScannerRunStatusResponse;
import com.apex.backend.exception.BadRequestException;
import com.apex.backend.exception.NotFoundException;
import com.apex.backend.model.ScannerRun;
import com.apex.backend.model.ScannerRunResult;
import com.apex.backend.repository.ScannerRunRepository;
import com.apex.backend.repository.ScannerRunResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScannerRunService {

    private final ManualScanService manualScanService;
    private final WatchlistService watchlistService;
    private final StrategyConfig strategyConfig;
    private final ScannerRunRepository scannerRunRepository;
    private final ScannerRunResultRepository scannerRunResultRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Qualifier("tradingExecutor")
    private final Executor tradingExecutor;

    @Transactional
    public ScannerRunResponse startRun(Long userId, String idempotencyKey, ScannerRunRequest request) {
        validateRequest(request);

        return idempotencyService.execute(userId, idempotencyKey, request, ScannerRunResponse.class, () -> {
            ScannerRun run = ScannerRun.builder()
                    .userId(userId)
                    .status(ScannerRun.Status.PENDING)
                    .universeType(request.getUniverseType().name())
                    .universePayload(serialize(resolveUniversePayload(request)))
                    .strategyId(request.getStrategyId())
                    .optionsPayload(serialize(request.getOptions()))
                    .dryRun(request.isDryRun())
                    .mode(resolveMode(request))
                    .createdAt(Instant.now())
                    .build();

            scannerRunRepository.save(run);
            Long runId = run.getId();

            // IMPORTANT: only start async after the TX commits; otherwise the async thread may not find the run row.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tradingExecutor.execute(() -> executeRun(runId, userId, request));
                }
            });

            return ScannerRunResponse.builder()
                    .runId(runId)
                    .status(run.getStatus().name())
                    .createdAt(run.getCreatedAt())
                    .build();
        });
    }

    @Transactional(readOnly = true)
    public ScannerRunStatusResponse getStatus(Long userId, Long runId) {
        ScannerRun run = scannerRunRepository.findByIdAndUserId(runId, userId)
                .orElseThrow(() -> new NotFoundException("Scan run not found"));

        return ScannerRunStatusResponse.builder()
                .runId(run.getId())
                .status(run.getStatus().name())
                .createdAt(run.getCreatedAt())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .errorMessage(run.getErrorMessage())
                .diagnostics(toDiagnostics(run))
                .build();
    }

    @Transactional(readOnly = true)
    public ScannerRunResultResponse getResults(Long userId, Long runId) {
        ScannerRun run = scannerRunRepository.findByIdAndUserId(runId, userId)
                .orElseThrow(() -> new NotFoundException("Scan run not found"));

        List<ScanSignalResponse> signals = scannerRunResultRepository.findByRunIdOrderByScoreDesc(runId)
                .stream()
                .map(this::toSignalResponse)
                .toList();

        return ScannerRunResultResponse.builder()
                .runId(run.getId())
                .diagnostics(toDiagnostics(run))
                .signals(signals)
                .build();
    }

    @Transactional
    public ScannerRunStatusResponse cancel(Long userId, Long runId) {
        ScannerRun run = scannerRunRepository.findByIdAndUserId(runId, userId)
                .orElseThrow(() -> new NotFoundException("Scan run not found"));

        if (run.getStatus() == ScannerRun.Status.COMPLETED || run.getStatus() == ScannerRun.Status.FAILED) {
            return getStatus(userId, runId);
        }

        run.setStatus(ScannerRun.Status.CANCELLED);
        run.setCompletedAt(Instant.now());
        scannerRunRepository.save(run);

        return getStatus(userId, runId);
    }

    private void executeRun(Long runId, Long userId, ScannerRunRequest request) {
        try {
            ScannerRun run = scannerRunRepository.findById(runId)
                    .orElseThrow(() -> new NotFoundException("Scan run not found"));

            if (run.getStatus() == ScannerRun.Status.CANCELLED) return;

            run.setStatus(ScannerRun.Status.RUNNING);
            run.setStartedAt(Instant.now());
            scannerRunRepository.save(run);

            List<String> symbols = resolveSymbols(userId, request);

            // If your universe resolution returns empty, mark completed with zeros (so you can see it clearly in DB).
            if (symbols == null || symbols.isEmpty()) {
                run.setTotalSymbols(0);
                run.setPassedStage1(0);
                run.setPassedStage2(0);
                run.setFinalSignals(0);
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

            // Re-check cancelled before saving anything heavy
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
            log.error("Scanner run {} failed", runId, ex);

            // Make sure FAILED status is persisted even if the exception happened early.
            scannerRunRepository.findById(runId).ifPresent(r -> {
                r.setStatus(ScannerRun.Status.FAILED);
                r.setErrorMessage(ex.getMessage());
                r.setCompletedAt(Instant.now());
                scannerRunRepository.save(r);
            });
        }
    }

    private void updateRunWithResponse(ScannerRun run, ScanResponse response) {
        ScanDiagnosticsBreakdown diagnostics = response != null ? response.getDiagnostics() : null;
        if (diagnostics == null) return;

        run.setTotalSymbols(diagnostics.getTotalSymbols());
        run.setPassedStage1(diagnostics.getPassedStage1());
        run.setPassedStage2(diagnostics.getPassedStage2());
        run.setFinalSignals(diagnostics.getFinalSignals());
        run.setRejectedStage1ReasonCounts(serialize(diagnostics.getRejectedStage1ReasonCounts()));
        run.setRejectedStage2ReasonCounts(serialize(diagnostics.getRejectedStage2ReasonCounts()));
    }

    private void saveResults(ScannerRun run, List<ScanSignalResponse> signals) {
        if (signals == null || signals.isEmpty()) return;

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

    private void validateRequest(ScannerRunRequest request) {
        if (request == null || request.getUniverseType() == null) {
            throw new BadRequestException("universeType is required");
        }
        if (request.getUniverseType() == ScannerRunRequest.UniverseType.SYMBOLS) {
            if (request.getSymbols() == null || request.getSymbols().isEmpty()) {
                throw new BadRequestException("symbols are required for SYMBOLS universe");
            }
            if (request.getSymbols().size() > WatchlistService.MAX_SYMBOLS) {
                throw new BadRequestException("symbols cannot exceed " + WatchlistService.MAX_SYMBOLS);
            }
        }
    }

    private Map<String, Object> resolveUniversePayload(ScannerRunRequest request) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("universeType", request.getUniverseType().name());

        if (request.getSymbols() != null) payload.put("symbols", request.getSymbols());
        if (request.getWatchlistId() != null) payload.put("watchlistId", request.getWatchlistId());
        if (request.getIndex() != null) payload.put("index", request.getIndex());

        return payload;
    }

    private String resolveMode(ScannerRunRequest request) {
        ScannerRunRequest.Mode mode = request.getMode() != null ? request.getMode() : ScannerRunRequest.Mode.PAPER;
        return mode.name();
    }

    private String serialize(Object payload) {
        if (payload == null) return null;
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize scanner payload: {}", e.getMessage());
            return null;
        }
    }

    private ScanDiagnosticsBreakdown toDiagnostics(ScannerRun run) {
        Map<String, Long> stage1 = deserializeCounts(run.getRejectedStage1ReasonCounts());
        Map<String, Long> stage2 = deserializeCounts(run.getRejectedStage2ReasonCounts());

        return ScanDiagnosticsBreakdown.builder()
                .totalSymbols(defaultValue(run.getTotalSymbols()))
                .passedStage1(defaultValue(run.getPassedStage1()))
                .passedStage2(defaultValue(run.getPassedStage2()))
                .finalSignals(defaultValue(run.getFinalSignals()))
                .rejectedStage1ReasonCounts(stage1)
                .rejectedStage2ReasonCounts(stage2)
                .build();
    }

    private Map<String, Long> deserializeCounts(String payload) {
        if (payload == null || payload.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(
                    payload,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Long.class)
            );
        } catch (Exception e) {
            log.warn("Failed to deserialize scanner counts: {}", e.getMessage());
            return Map.of();
        }
    }

    private int defaultValue(Integer value) {
        return value == null ? 0 : value;
    }

    private ScanSignalResponse toSignalResponse(ScannerRunResult result) {
        return ScanSignalResponse.builder()
                .symbol(result.getSymbol())
                .score(result.getScore() != null ? result.getScore() : 0.0)
                .grade(result.getGrade())
                .entryPrice(result.getEntryPrice() != null ? result.getEntryPrice() : 0.0)
                .scanTime(result.getCreatedAt() != null
                        ? result.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                        : null)
                .reason(result.getReason())
                .build();
    }
}
