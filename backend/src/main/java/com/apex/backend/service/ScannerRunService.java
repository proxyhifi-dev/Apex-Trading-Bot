package com.apex.backend.service;

import com.apex.backend.dto.ScanDiagnosticsBreakdown;
import com.apex.backend.dto.ScanSignalResponse;
import com.apex.backend.dto.ScanRunSummary;
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
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScannerRunService {

    private final ScannerRunRepository scannerRunRepository;
    private final ScannerRunResultRepository scannerRunResultRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final ScannerRunExecutor scannerRunExecutor;
    private final WatchlistService watchlistService;
    @Qualifier("scannerExecutor")
    private final Executor scannerExecutor;

    @Transactional
    public ScannerRunResponse startRun(Long userId, String idempotencyKey, ScannerRunRequest request) {
        ScannerRunRequest normalizedRequest = normalizeRequest(request);
        validateRequest(normalizedRequest);
        String correlationId = MDC.get("correlationId");

        return idempotencyService.execute(userId, idempotencyKey, normalizedRequest, ScannerRunResponse.class, () -> {
            List<String> resolvedSymbols = null;
            if (normalizedRequest.getStrategyId() != null) {
                resolvedSymbols = watchlistService.resolveSymbolsForStrategyOrDefault(userId, normalizedRequest.getStrategyId());
            }
            Map<String, Object> universePayload = resolveUniversePayload(normalizedRequest, resolvedSymbols);

            ScannerRun run = ScannerRun.builder()
                    .userId(userId)
                    .status(ScannerRun.Status.PENDING)
                    .universeType(normalizedRequest.getUniverseType().name())
                    .universePayload(serialize(universePayload))
                    .strategyId(normalizedRequest.getStrategyId() != null ? normalizedRequest.getStrategyId().toString() : null)
                    .optionsPayload(serialize(normalizedRequest.getOptions()))
                    .dryRun(normalizedRequest.isDryRun())
                    .mode(resolveMode(normalizedRequest))
                    .totalSymbols(0)
                    .passedStage1(0)
                    .passedStage2(0)
                    .finalSignals(0)
                    .stagePassCounts(serialize(Map.of()))
                    .rejectedStage1ReasonCounts(serialize(Map.of()))
                    .rejectedStage2ReasonCounts(serialize(Map.of()))
                    .createdAt(Instant.now())
                    .build();

            scannerRunRepository.save(run);
            Long runId = run.getId();
            log.info("Manual scan requested: runId={}, userId={}", runId, userId);

            Runnable executorTask = () -> {
                if (correlationId != null) {
                    MDC.put("correlationId", correlationId);
                }
                MDC.put("runId", String.valueOf(runId));
                try {
                    scannerRunExecutor.executeRun(runId, userId, correlationId, normalizedRequest);
                } catch (Exception e) {
                    log.error("CRITICAL: Async scan task crashed for runId: {}", runId, e);
                    failRunSafely(runId, "System Error: Scan execution crashed.");
                } finally {
                    MDC.clear();
                }
            };

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            log.info("Submitting scan executor task after commit: runId={}, userId={}", runId, userId);
                            scannerExecutor.execute(executorTask);
                        } catch (TaskRejectedException e) {
                            log.error("Scan task rejected (Queue Full) for runId: {}, userId={}", runId, userId);
                            failRunSafely(runId, "System Busy: Scan queue is full. Try again later.");
                        } catch (Exception e) {
                            log.error("Failed to submit scan task for runId: {}, userId={}", runId, userId, e);
                            failRunSafely(runId, "System Error: Failed to submit scan task.");
                        }
                    }
                });
            } else {
                try {
                    log.info("Submitting scan executor task immediately: runId={}, userId={}", runId, userId);
                    scannerExecutor.execute(executorTask);
                } catch (Exception e) {
                    log.error("Immediate scan submission failed for runId: {}, userId={}", runId, userId, e);
                    failRunSafely(runId, "System Error: Failed to submit scan task.");
                    throw new BadRequestException("System busy, cannot start scan right now.");
                }
            }

            return ScannerRunResponse.builder()
                    .runId(runId)
                    .status(run.getStatus().name())
                    .createdAt(run.getCreatedAt())
                    .build();
        });
    }

    public void failRunSafely(Long runId, String reason) {
        CompletableFuture.runAsync(() -> {
            try {
                scannerRunRepository.findById(runId).ifPresent(r -> {
                    if (r.getStatus() == ScannerRun.Status.PENDING) {
                        r.setStatus(ScannerRun.Status.FAILED);
                        if (r.getStartedAt() == null) {
                            r.setStartedAt(Instant.now());
                        }
                        r.setErrorMessage(reason);
                        if (r.getTotalSymbols() == null) {
                            r.setTotalSymbols(0);
                        }
                        if (r.getPassedStage1() == null) {
                            r.setPassedStage1(0);
                        }
                        if (r.getPassedStage2() == null) {
                            r.setPassedStage2(0);
                        }
                        if (r.getFinalSignals() == null) {
                            r.setFinalSignals(0);
                        }
                        if (r.getStagePassCounts() == null) {
                            r.setStagePassCounts(serialize(Map.of()));
                        }
                        if (r.getRejectedStage1ReasonCounts() == null) {
                            r.setRejectedStage1ReasonCounts(serialize(Map.of()));
                        }
                        if (r.getRejectedStage2ReasonCounts() == null) {
                            r.setRejectedStage2ReasonCounts(serialize(Map.of()));
                        }
                        r.setCompletedAt(Instant.now());
                        scannerRunRepository.save(r);
                        log.info("Forcefully marked runId {} as FAILED. Reason: {}", runId, reason);
                    }
                });
            } catch (Exception e) {
                log.error("Failed to update run status to FAILED for runId: {}", runId, e);
            }
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

    @Transactional(readOnly = true)
    public ScanRunSummary getLatestSummary(Long userId) {
        ScannerRun run = scannerRunRepository.findTopByUserIdOrderByIdDesc(userId)
                .orElseThrow(() -> new NotFoundException("No scanner runs found"));

        return ScanRunSummary.builder()
                .scanId(run.getId())
                .startedAt(run.getStartedAt())
                .endedAt(run.getCompletedAt())
                .symbolsScanned(defaultValue(run.getTotalSymbols()))
                .stagePassCounts(deserializeCounts(run.getStagePassCounts()))
                .signalsFound(defaultValue(run.getFinalSignals()))
                .status(run.getStatus().name())
                .errors(run.getErrorMessage() != null ? List.of(run.getErrorMessage()) : List.of())
                .build();
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

    private ScannerRunRequest normalizeRequest(ScannerRunRequest request) {
        return request;
    }

    private Map<String, Object> resolveUniversePayload(ScannerRunRequest request, List<String> resolvedSymbols) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("universeType", request.getUniverseType().name());

        if (resolvedSymbols != null) {
            payload.put("symbols", resolvedSymbols);
        } else if (request.getSymbols() != null) {
            payload.put("symbols", request.getSymbols());
        }
        if (request.getWatchlistId() != null) payload.put("watchlistId", request.getWatchlistId());
        if (request.getIndex() != null) payload.put("index", request.getIndex());
        if (request.getStrategyId() != null) payload.put("strategyId", request.getStrategyId());

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
