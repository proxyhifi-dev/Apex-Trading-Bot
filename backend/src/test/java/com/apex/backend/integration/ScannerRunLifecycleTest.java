package com.apex.backend.integration;

import com.apex.backend.dto.ScanDiagnosticsBreakdown;
import com.apex.backend.dto.ScanResponse;
import com.apex.backend.dto.ScannerRunRequest;
import com.apex.backend.model.ScannerRun;
import com.apex.backend.repository.ScannerRunRepository;
import com.apex.backend.service.ManualScanService;
import com.apex.backend.service.ScannerRunService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
class ScannerRunLifecycleTest {

    @TestConfiguration
    static class SyncExecutorConfig {
        @Bean(name = "scannerExecutor")
        @Primary
        public Executor scannerExecutor() {
            return Runnable::run;
        }
    }

    @MockBean
    private ManualScanService manualScanService;

    @Autowired
    private ScannerRunService scannerRunService;

    @Autowired
    private ScannerRunRepository scannerRunRepository;

    @Test
    void scannerRunTransitionsFromPendingToCompleted() {
        ScanDiagnosticsBreakdown diagnostics = ScanDiagnosticsBreakdown.builder()
                .totalSymbols(1)
                .passedStage1(1)
                .passedStage2(1)
                .finalSignals(1)
                .rejectedStage1ReasonCounts(Map.of())
                .rejectedStage2ReasonCounts(Map.of())
                .build();
        ScanResponse response = ScanResponse.builder()
                .requestId("test-request")
                .startedAt(Instant.now())
                .durationMs(10)
                .symbolsScanned(1)
                .diagnostics(diagnostics)
                .signals(List.of())
                .build();

        when(manualScanService.runManualScan(anyLong(), any()))
                .thenReturn(response);

        ScannerRunRequest request = ScannerRunRequest.builder()
                .universeType(ScannerRunRequest.UniverseType.SYMBOLS)
                .symbols(List.of("NSE:TEST"))
                .dryRun(true)
                .build();

        Long userId = 77L;
        Long runId = scannerRunService.startRun(userId, null, request).getRunId();

        ScannerRun run = awaitRun(runId);

        assertThat(run.getStatus()).isEqualTo(ScannerRun.Status.COMPLETED);
        assertThat(run.getStartedAt()).isNotNull();
        assertThat(run.getCompletedAt()).isNotNull();
        assertThat(run.getTotalSymbols()).isEqualTo(1);
        assertThat(run.getPassedStage1()).isEqualTo(1);
        assertThat(run.getPassedStage2()).isEqualTo(1);
        assertThat(run.getFinalSignals()).isEqualTo(1);
    }

    private ScannerRun awaitRun(Long runId) {
        long timeoutMs = 2000;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            ScannerRun run = scannerRunRepository.findById(runId).orElseThrow();
            if (run.getStatus() == ScannerRun.Status.COMPLETED || run.getStatus() == ScannerRun.Status.FAILED) {
                return run;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return scannerRunRepository.findById(runId).orElseThrow();
    }
}
