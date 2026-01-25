package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.dto.ScanDiagnosticsBreakdown;
import com.apex.backend.dto.ScanResponse;
import com.apex.backend.dto.ScannerRunRequest;
import com.apex.backend.model.ScannerRun;
import com.apex.backend.repository.ScannerRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.TestConfiguration;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({ScannerRunExecutor.class, ScannerRunExecutorTest.TestConfig.class})
class ScannerRunExecutorTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private ScannerRunRepository scannerRunRepository;

    @Autowired
    private ScannerRunExecutor scannerRunExecutor;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ManualScanService manualScanService;

    @MockBean
    private WatchlistService watchlistService;

    @MockBean
    private StrategyConfig strategyConfig;

    @Test
    void executeRunMarksFailedAndPersistsError() {
        StrategyConfig.Scanner scanner = new StrategyConfig.Scanner();
        scanner.setEnabled(true);
        when(strategyConfig.getScanner()).thenReturn(scanner);
        when(watchlistService.normalizeSymbols(List.of("NSE:AAA"))).thenReturn(List.of("NSE:AAA"));

        ScannerRun run = scannerRunRepository.save(ScannerRun.builder()
                .userId(42L)
                .status(ScannerRun.Status.PENDING)
                .universeType(ScannerRunRequest.UniverseType.SYMBOLS.name())
                .dryRun(true)
                .mode(ScannerRunRequest.Mode.PAPER.name())
                .createdAt(Instant.now())
                .build());

        ScannerRunRequest request = ScannerRunRequest.builder()
                .universeType(ScannerRunRequest.UniverseType.SYMBOLS)
                .symbols(List.of("NSE:AAA"))
                .timeframe("5")
                .regime("BULL")
                .build();

        when(manualScanService.runManualScan(anyLong(), any()))
                .thenThrow(new RuntimeException("boom"));

        scannerRunExecutor.executeRun(run.getId(), 42L, request);

        ScannerRun updated = scannerRunRepository.findById(run.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ScannerRun.Status.FAILED);
        assertThat(updated.getStartedAt()).isNotNull();
        assertThat(updated.getCompletedAt()).isNotNull();
        assertThat(updated.getErrorMessage()).contains("boom");
        assertThat(updated.getTotalSymbols()).isEqualTo(0);
        assertThat(updated.getRejectedStage1ReasonCounts()).isNotNull();
        assertThat(updated.getRejectedStage2ReasonCounts()).isNotNull();
    }

    @Test
    void executeRunFailsWhenScannerDisabled() {
        StrategyConfig.Scanner scanner = new StrategyConfig.Scanner();
        scanner.setEnabled(false);
        when(strategyConfig.getScanner()).thenReturn(scanner);
        when(watchlistService.normalizeSymbols(List.of("NSE:AAA"))).thenReturn(List.of("NSE:AAA"));

        ScannerRun run = scannerRunRepository.save(ScannerRun.builder()
                .userId(7L)
                .status(ScannerRun.Status.PENDING)
                .universeType(ScannerRunRequest.UniverseType.SYMBOLS.name())
                .dryRun(true)
                .mode(ScannerRunRequest.Mode.PAPER.name())
                .createdAt(Instant.now())
                .build());

        ScannerRunRequest request = ScannerRunRequest.builder()
                .universeType(ScannerRunRequest.UniverseType.SYMBOLS)
                .symbols(List.of("NSE:AAA"))
                .timeframe("5")
                .regime("BULL")
                .build();

        scannerRunExecutor.executeRun(run.getId(), 7L, request);

        ScannerRun updated = scannerRunRepository.findById(run.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ScannerRun.Status.FAILED);
        assertThat(updated.getStartedAt()).isNotNull();
        assertThat(updated.getCompletedAt()).isNotNull();
        assertThat(updated.getErrorMessage()).isEqualTo("Scanner disabled");
    }

    @Test
    void executeRunCompletesWithEmptyUniverseDiagnostics() throws Exception {
        StrategyConfig.Scanner scanner = new StrategyConfig.Scanner();
        scanner.setEnabled(true);
        when(strategyConfig.getScanner()).thenReturn(scanner);
        when(watchlistService.resolveSymbolsForStrategyOrDefault(42L, 7L)).thenReturn(List.of());

        ScannerRun run = scannerRunRepository.save(ScannerRun.builder()
                .userId(42L)
                .status(ScannerRun.Status.PENDING)
                .universeType(ScannerRunRequest.UniverseType.WATCHLIST.name())
                .dryRun(true)
                .mode(ScannerRunRequest.Mode.PAPER.name())
                .createdAt(Instant.now())
                .build());

        ScannerRunRequest request = ScannerRunRequest.builder()
                .universeType(ScannerRunRequest.UniverseType.WATCHLIST)
                .strategyId(7L)
                .timeframe("5")
                .regime("BULL")
                .build();

        scannerRunExecutor.executeRun(run.getId(), 42L, request);

        ScannerRun updated = scannerRunRepository.findById(run.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ScannerRun.Status.COMPLETED);
        assertThat(updated.getTotalSymbols()).isEqualTo(0);
        java.util.Map<String, Long> stage1 = objectMapper.readValue(
                updated.getRejectedStage1ReasonCounts(),
                objectMapper.getTypeFactory().constructMapType(java.util.Map.class, String.class, Long.class)
        );
        assertThat(stage1).containsKey("EMPTY_UNIVERSE");
    }

    @Test
    void executeRunTransitionsToCompletedWithDiagnostics() {
        StrategyConfig.Scanner scanner = new StrategyConfig.Scanner();
        scanner.setEnabled(true);
        when(strategyConfig.getScanner()).thenReturn(scanner);
        when(watchlistService.normalizeSymbols(List.of("NSE:AAA"))).thenReturn(List.of("NSE:AAA"));

        ScannerRun run = scannerRunRepository.save(ScannerRun.builder()
                .userId(99L)
                .status(ScannerRun.Status.PENDING)
                .universeType(ScannerRunRequest.UniverseType.SYMBOLS.name())
                .dryRun(true)
                .mode(ScannerRunRequest.Mode.PAPER.name())
                .createdAt(Instant.now())
                .build());

        ScanResponse response = ScanResponse.builder()
                .diagnostics(ScanDiagnosticsBreakdown.builder()
                        .totalSymbols(1)
                        .passedStage1(1)
                        .passedStage2(1)
                        .finalSignals(0)
                        .rejectedStage1ReasonCounts(java.util.Map.of())
                        .rejectedStage2ReasonCounts(java.util.Map.of())
                        .build())
                .signals(List.of())
                .build();

        when(manualScanService.runManualScan(anyLong(), any()))
                .thenReturn(response);

        ScannerRunRequest request = ScannerRunRequest.builder()
                .universeType(ScannerRunRequest.UniverseType.SYMBOLS)
                .symbols(List.of("NSE:AAA"))
                .timeframe("5")
                .regime("BULL")
                .build();

        scannerRunExecutor.executeRun(run.getId(), 99L, request);

        ScannerRun updated = scannerRunRepository.findById(run.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ScannerRun.Status.COMPLETED);
        assertThat(updated.getStartedAt()).isNotNull();
        assertThat(updated.getCompletedAt()).isNotNull();
        assertThat(updated.getTotalSymbols()).isEqualTo(1);
        assertThat(updated.getRejectedStage1ReasonCounts()).isNotNull();
        assertThat(updated.getRejectedStage2ReasonCounts()).isNotNull();
    }
}
