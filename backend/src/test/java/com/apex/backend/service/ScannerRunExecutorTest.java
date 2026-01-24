package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
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
}
