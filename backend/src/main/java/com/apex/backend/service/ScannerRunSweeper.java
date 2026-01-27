package com.apex.backend.service;

import com.apex.backend.model.ScannerRun;
import com.apex.backend.repository.ScannerRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScannerRunSweeper {

    private static final Duration PENDING_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration RUNNING_TIMEOUT = Duration.ofMinutes(20);

    private final ScannerRunRepository scannerRunRepository;

    @Scheduled(fixedDelayString = "${apex.scanner.sweeper-interval-ms:120000}")
    @Transactional
    public void sweepStuckRuns() {
        Instant now = Instant.now();
        List<ScannerRun> pendingRuns = scannerRunRepository.findByStatusAndCreatedAtBefore(
                ScannerRun.Status.PENDING,
                now.minus(PENDING_TIMEOUT)
        );
        List<ScannerRun> runningRuns = scannerRunRepository.findByStatusAndStartedAtBefore(
                ScannerRun.Status.RUNNING,
                now.minus(RUNNING_TIMEOUT)
        );

        pendingRuns.forEach(run -> markFailed(run, "stuck run cleanup"));
        runningRuns.forEach(run -> markFailed(run, "stuck run cleanup"));
    }

    private void markFailed(ScannerRun run, String reason) {
        MDC.put("runId", String.valueOf(run.getId()));
        try {
            if (run.getStartedAt() == null) {
                run.setStartedAt(Instant.now());
            }
            run.setStatus(ScannerRun.Status.FAILED);
            run.setErrorMessage(reason);
            run.setCompletedAt(Instant.now());
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
            if (run.getRejectedStage1ReasonCounts() == null) {
                run.setRejectedStage1ReasonCounts("{}");
            }
            if (run.getRejectedStage2ReasonCounts() == null) {
                run.setRejectedStage2ReasonCounts("{}");
            }
            scannerRunRepository.save(run);
            log.warn("Sweeper marked scan run as FAILED: runId={}, reason={}", run.getId(), reason);
        } finally {
            MDC.remove("runId");
        }
    }
}
