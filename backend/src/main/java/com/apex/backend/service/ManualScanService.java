package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.dto.ScanDiagnosticsBreakdown;
import com.apex.backend.dto.ScanDiagnosticsReason;
import com.apex.backend.dto.ScanError;
import com.apex.backend.dto.ScanPipelineStats;
import com.apex.backend.dto.ScanRejectReasonCount;
import com.apex.backend.dto.ScanRequest;
import com.apex.backend.dto.ScanResponse;
import com.apex.backend.dto.ScanSignalResponse;
import com.apex.backend.exception.ConflictException;
import com.apex.backend.model.Candle;
import com.apex.backend.service.indicator.MarketRegimeDetector;
import com.apex.backend.trading.pipeline.DecisionResult;
import com.apex.backend.trading.pipeline.PipelineRequest;
import com.apex.backend.trading.pipeline.ScanRejectReason;
import com.apex.backend.trading.pipeline.SignalDiagnostics;
import com.apex.backend.trading.pipeline.SignalScore;
import com.apex.backend.trading.pipeline.TradeDecisionPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class ManualScanService {

    private final StrategyConfig config;
    private final ScannerUniverseResolver universeResolver;
    private final ScannerTimeframeMapper timeframeMapper;
    private final StockScreeningService screeningService;
    private final FyersService fyersService;
    private final TradeDecisionPipelineService tradeDecisionPipelineService;
    private final TradeExecutionService tradeExecutionService;
    private final PaperOrderService paperOrderService;
    private final MarketRegimeDetector marketRegimeDetector;
    @Qualifier("tradingExecutor")
    private final Executor tradingExecutor;

    private final AtomicBoolean scanInProgress = new AtomicBoolean(false);

    public ScanResponse runManualScan(Long userId, ScanRequest request) {
        ensureScannerEnabled();
        if (!scanInProgress.compareAndSet(false, true)) {
            throw new ConflictException("scan already in progress");
        }
        Instant startedAt = Instant.now();
        String requestId = resolveRequestId();
        try {
            List<String> universe = resolveUniverse(request);
            String timeframe = timeframeMapper.toFyersTimeframe(request.getTf());
            boolean marketBullish = resolveMarketRegime(request);
            log.info("Manual scan: universe={} tf={} regime={} bullish={}", request.getUniverse(), timeframe, request.getRegime(), marketBullish);

            ConcurrentLinkedQueue<ScanSymbolOutcome> outcomes = new ConcurrentLinkedQueue<>();
            List<CompletableFuture<Void>> futures = universe.stream()
                    .map(symbol -> CompletableFuture.runAsync(() -> outcomes.add(scanSymbol(userId, symbol, timeframe)), tradingExecutor))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<ScanSignalResponse> signals = new ArrayList<>();
            List<DecisionResult> candidateDecisions = new ArrayList<>();
            List<ScanError> errors = new ArrayList<>();
            ScanPipelineStats pipelineStats = new ScanPipelineStats();
            Map<ScanRejectReason, Long> rejectCounts = new EnumMap<>(ScanRejectReason.class);
            ScanDiagnosticsBreakdown diagnostics = ScanDiagnosticsBreakdown.builder()
                    .totalSymbols(outcomes.size())
                    .build();
            Map<ScanDiagnosticsReason, Long> stage1Rejects = new EnumMap<>(ScanDiagnosticsReason.class);
            Map<ScanDiagnosticsReason, Long> stage2Rejects = new EnumMap<>(ScanDiagnosticsReason.class);

            for (ScanSymbolOutcome outcome : outcomes) {
                if (outcome.dataMissing) {
                    incrementReject(stage1Rejects, ScanDiagnosticsReason.DATA_MISSING);
                    incrementReject(rejectCounts, ScanRejectReason.INSUFFICIENT_DATA);
                    continue;
                }
                if (outcome.error != null) {
                    errors.add(ScanError.builder()
                            .symbol(outcome.symbol)
                            .message(outcome.error)
                            .build());
                    incrementReject(rejectCounts, ScanRejectReason.UNKNOWN);
                    incrementReject(stage1Rejects, ScanDiagnosticsReason.DATA_MISSING);
                    continue;
                }
                DecisionResult decision = outcome.decision;
                if (decision == null) {
                    incrementReject(rejectCounts, ScanRejectReason.UNKNOWN);
                    incrementReject(stage1Rejects, ScanDiagnosticsReason.DATA_MISSING);
                    continue;
                }
                updatePipelineStats(pipelineStats, decision.signalScore());
                collectRejectReasons(rejectCounts, decision.signalScore());
                trackDiagnostics(decision.signalScore(), diagnostics, stage1Rejects, stage2Rejects);
                if (decision.action() == DecisionResult.DecisionAction.BUY) {
                    SignalScore score = decision.signalScore();
                    signals.add(ScanSignalResponse.builder()
                            .symbol(decision.symbol())
                            .score(decision.score())
                            .grade(score != null ? score.grade() : null)
                            .entryPrice(score != null ? score.entryPrice() : 0.0)
                            .scanTime(LocalDateTime.now())
                            .reason(score != null ? score.reason() : null)
                            .build());
                    candidateDecisions.add(decision);
                }
            }

            int finalSignals = signals.size();
            pipelineStats.setFinalSignals(finalSignals);
            diagnostics.setFinalSignals(finalSignals);
            List<ScanRejectReasonCount> rejectReasonTop = rejectCounts.entrySet().stream()
                    .sorted(Map.Entry.<ScanRejectReason, Long>comparingByValue().reversed())
                    .limit(10)
                    .map(entry -> ScanRejectReasonCount.builder()
                            .reason(entry.getKey().name())
                            .count(entry.getValue())
                            .build())
                    .toList();
            diagnostics.setRejectedStage1ReasonCounts(toReasonMap(stage1Rejects));
            diagnostics.setRejectedStage2ReasonCounts(toReasonMap(stage2Rejects));

            if (!request.isDryRun()) {
                processCandidates(candidateDecisions, userId);
            }

            long durationMs = java.time.Duration.between(startedAt, Instant.now()).toMillis();
            return ScanResponse.builder()
                    .requestId(requestId)
                    .startedAt(startedAt)
                    .durationMs(durationMs)
                    .symbolsScanned(outcomes.size())
                    .pipeline(pipelineStats)
                    .diagnostics(diagnostics)
                    .rejectReasonsTop(rejectReasonTop)
                    .signals(signals)
                    .errors(errors)
                    .build();
        } finally {
            scanInProgress.set(false);
        }
    }

    private ScanSymbolOutcome scanSymbol(Long userId, String symbol, String timeframe) {
        try {
            List<Candle> candles = fyersService.getHistoricalData(symbol, 200, timeframe);
            if (candles == null || candles.isEmpty()) {
                return ScanSymbolOutcome.dataMissing(symbol);
            }
            DecisionResult decision = tradeDecisionPipelineService.evaluate(new PipelineRequest(
                    userId,
                    symbol,
                    timeframe,
                    candles,
                    null
            ));
            return ScanSymbolOutcome.success(symbol, decision);
        } catch (Exception ex) {
            log.warn("Manual scan error for {}: {}", symbol, ex.getMessage());
            return ScanSymbolOutcome.failure(symbol, ex.getMessage());
        }
    }

    private void processCandidates(List<DecisionResult> candidates, Long userId) {
        if (candidates.isEmpty()) {
            return;
        }
        candidates.sort(Comparator.comparingDouble(DecisionResult::score).reversed());
        int maxCandidates = config.getScanner().getMaxCandidates();
        List<DecisionResult> topSignals = candidates.stream().limit(maxCandidates).toList();
        boolean requireManualApproval = config.getScanner().isRequireManualApproval();
        double currentVix = fetchVix();
        for (DecisionResult decision : topSignals) {
            if (config.getTrading().isPaperSignalOrdersEnabled()) {
                paperOrderService.placeFromSignal(userId, decision);
            }
            if (requireManualApproval) {
                screeningService.saveSignal(userId, decision);
            } else {
                tradeExecutionService.executeAutoTrade(userId, decision, true, currentVix);
            }
        }
    }

    private double fetchVix() {
        try {
            List<Candle> vixData = fyersService.getHistoricalData("NSE:INDIAVIX-INDEX", 1, "D");
            if (!vixData.isEmpty()) {
                return vixData.get(vixData.size() - 1).getClose();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch VIX: {}", e.getMessage());
        }
        return 15.0;
    }

    private void updatePipelineStats(ScanPipelineStats stats, SignalScore signalScore) {
        if (stats == null || signalScore == null || signalScore.diagnostics() == null) {
            return;
        }
        SignalDiagnostics diagnostics = signalScore.diagnostics();
        if (diagnostics.isTrendPass()) {
            stats.setTrendPassed(stats.getTrendPassed() + 1);
        }
        if (diagnostics.isVolumePass()) {
            stats.setVolumePassed(stats.getVolumePassed() + 1);
        }
        if (diagnostics.isBreakoutPass()) {
            stats.setBreakoutPassed(stats.getBreakoutPassed() + 1);
        }
        if (diagnostics.isRsiPass()) {
            stats.setRsiPassed(stats.getRsiPassed() + 1);
        }
        if (diagnostics.isAdxPass()) {
            stats.setAdxPassed(stats.getAdxPassed() + 1);
        }
        if (diagnostics.isAtrPass()) {
            stats.setAtrPassed(stats.getAtrPassed() + 1);
        }
        if (diagnostics.isMomentumPass()) {
            stats.setMomentumPassed(stats.getMomentumPassed() + 1);
        }
        if (diagnostics.isSqueezePass()) {
            stats.setSqueezePassed(stats.getSqueezePassed() + 1);
        }
    }

    private void collectRejectReasons(Map<ScanRejectReason, Long> counters, SignalScore signalScore) {
        if (signalScore == null || signalScore.diagnostics() == null) {
            incrementReject(counters, ScanRejectReason.UNKNOWN);
            return;
        }
        List<ScanRejectReason> reasons = signalScore.diagnostics().getRejectionReasons();
        if (reasons == null || reasons.isEmpty()) {
            incrementReject(counters, ScanRejectReason.UNKNOWN);
            return;
        }
        for (ScanRejectReason reason : reasons) {
            incrementReject(counters, reason);
        }
    }

    private void incrementReject(Map<ScanRejectReason, Long> counters, ScanRejectReason reason) {
        counters.merge(reason, 1L, Long::sum);
    }

    private void incrementReject(Map<ScanDiagnosticsReason, Long> counters, ScanDiagnosticsReason reason) {
        counters.merge(reason, 1L, Long::sum);
    }

    private void trackDiagnostics(SignalScore signalScore, ScanDiagnosticsBreakdown diagnostics,
                                  Map<ScanDiagnosticsReason, Long> stage1Rejects,
                                  Map<ScanDiagnosticsReason, Long> stage2Rejects) {
        if (signalScore == null || signalScore.diagnostics() == null) {
            incrementReject(stage1Rejects, ScanDiagnosticsReason.DATA_MISSING);
            return;
        }
        SignalDiagnostics details = signalScore.diagnostics();
        boolean stage1Pass = details.isTrendPass() && details.isVolumePass() && details.isBreakoutPass();
        if (stage1Pass) {
            diagnostics.setPassedStage1(diagnostics.getPassedStage1() + 1);
        } else {
            return;
        }
        boolean stage2Pass = details.isRsiPass() && details.isAdxPass() && details.isAtrPass()
                && details.isMomentumPass() && details.isSqueezePass();
        if (stage2Pass) {
            diagnostics.setPassedStage2(diagnostics.getPassedStage2() + 1);
        } else {
            if (!details.isAdxPass()) {
                incrementReject(stage2Rejects, ScanDiagnosticsReason.ADX_FAIL);
            }
            if (!details.isRsiPass()) {
                incrementReject(stage2Rejects, ScanDiagnosticsReason.RSI_FAIL);
            }
            if (!details.isMomentumPass()) {
                incrementReject(stage2Rejects, ScanDiagnosticsReason.MACD_FAIL);
            }
            if (!details.isAtrPass()) {
                incrementReject(stage2Rejects, ScanDiagnosticsReason.VOLATILITY_FAIL);
            }
        }
    }

    private Map<String, Long> toReasonMap(Map<ScanDiagnosticsReason, Long> counts) {
        return counts.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue));
    }

    private void ensureScannerEnabled() {
        if (!config.getScanner().isEnabled()) {
            throw new ConflictException("Scanner disabled");
        }
    }

    private boolean resolveMarketRegime(ScanRequest request) {
        return switch (request.getRegime()) {
            case BULL -> true;
            case BEAR -> false;
            case AUTO -> checkMarketRegime();
        };
    }

    private boolean checkMarketRegime() {
        try {
            List<Candle> niftyData = fyersService.getHistoricalData("NSE:NIFTY50-INDEX", 200, "D");
            if (niftyData.isEmpty()) {
                return true;
            }
            var regime = marketRegimeDetector.detectAndStore("NSE:NIFTY50-INDEX", "1d", niftyData);
            return regime == com.apex.backend.model.MarketRegime.TRENDING
                    || regime == com.apex.backend.model.MarketRegime.LOW_VOL;
        } catch (Exception e) {
            return true;
        }
    }

    private String resolveRequestId() {
        String requestId = MDC.get("requestId");
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }

    private List<String> resolveUniverse(ScanRequest request) {
        return universeResolver.resolveUniverse(request);
    }

    private record ScanSymbolOutcome(String symbol, DecisionResult decision, String error, boolean dataMissing) {
        static ScanSymbolOutcome success(String symbol, DecisionResult decision) {
            return new ScanSymbolOutcome(symbol, decision, null, false);
        }

        static ScanSymbolOutcome dataMissing(String symbol) {
            return new ScanSymbolOutcome(symbol, null, null, true);
        }

        static ScanSymbolOutcome failure(String symbol, String error) {
            return new ScanSymbolOutcome(symbol, null, error, false);
        }
    }
}
