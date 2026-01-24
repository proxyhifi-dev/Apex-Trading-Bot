package com.apex.backend.service;

import com.apex.backend.dto.ScanRequest;
import com.apex.backend.dto.ScanResponse;
import com.apex.backend.model.Candle;
import com.apex.backend.trading.pipeline.DecisionResult;
import com.apex.backend.trading.pipeline.PipelineRequest;
import com.apex.backend.trading.pipeline.RiskDecision;
import com.apex.backend.trading.pipeline.ScanRejectReason;
import com.apex.backend.trading.pipeline.SignalDiagnostics;
import com.apex.backend.trading.pipeline.SignalScore;
import com.apex.backend.trading.pipeline.TradeDecisionPipelineService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest(properties = {
        "jwt.secret=01234567890123456789012345678901",
        "apex.scanner.enabled=true"
})
class ManualScanServiceTest {

    @Autowired
    private ManualScanService manualScanService;

    @MockBean
    private FyersService fyersService;

    @MockBean
    private TradeDecisionPipelineService tradeDecisionPipelineService;

    @MockBean
    private TradeExecutionService tradeExecutionService;

    @MockBean
    private StockScreeningService stockScreeningService;

    @MockBean
    private com.apex.backend.service.indicator.MarketRegimeDetector marketRegimeDetector;

    @Test
    void diagnosticsReturnEvenWhenNoSignals() {
        Mockito.when(fyersService.getHistoricalData(Mockito.anyString(), Mockito.anyInt(), Mockito.anyString()))
                .thenReturn(sampleCandles());
        Mockito.when(tradeDecisionPipelineService.evaluate(any(PipelineRequest.class)))
                .thenAnswer(invocation -> {
                    PipelineRequest req = invocation.getArgument(0);
                    SignalDiagnostics diagnostics = SignalDiagnostics.builder()
                            .trendPass(true)
                            .volumePass(true)
                            .breakoutPass(false)
                            .rsiPass(false)
                            .adxPass(false)
                            .atrPass(true)
                            .momentumPass(false)
                            .squeezePass(false)
                            .build();
                    diagnostics.addRejectionReason(ScanRejectReason.ADX_TOO_LOW);
                    SignalScore score = new SignalScore(false, 55.0, "N/A", 0.0, 0.0,
                            "Entry conditions not met", null, List.of(), diagnostics);
                    return new DecisionResult(
                            req.symbol(),
                            DecisionResult.DecisionAction.HOLD,
                            55.0,
                            List.of("Entry conditions not met"),
                            new RiskDecision(false, 0.0, List.of(), 1.0, 0),
                            null,
                            score,
                            null
                    );
                });

        ScanRequest request = ScanRequest.builder()
                .universe(ScanRequest.Universe.CUSTOM)
                .symbols(List.of("SBIN", "RELIANCE"))
                .tf("5m")
                .regime(ScanRequest.Regime.BULL)
                .dryRun(true)
                .build();

        ScanResponse response = manualScanService.runManualScan(42L, request);

        assertThat(response.getSymbolsScanned()).isEqualTo(2);
        assertThat(response.getPipeline().getTrendPassed()).isEqualTo(2);
        assertThat(response.getPipeline().getVolumePassed()).isEqualTo(2);
        assertThat(response.getPipeline().getBreakoutPassed()).isEqualTo(0);
        assertThat(response.getPipeline().getFinalSignals()).isZero();
        assertThat(response.getRejectReasonsTop())
                .anyMatch(reason -> reason.getReason().equals("ADX_TOO_LOW"));
    }

    @Test
    void diagnosticsPopulatedWhenUniverseEmpty() {
        ScanRequest request = ScanRequest.builder()
                .universe(ScanRequest.Universe.CUSTOM)
                .symbols(List.of())
                .tf("5m")
                .regime(ScanRequest.Regime.BULL)
                .dryRun(true)
                .build();

        ScanResponse response = manualScanService.runManualScan(42L, request);

        assertThat(response.getDiagnostics().getTotalSymbols()).isZero();
        assertThat(response.getDiagnostics().getRejectedStage1ReasonCounts())
                .containsKey("EMPTY_UNIVERSE");
    }

    @Test
    void diagnosticsPopulatedWhenDataMissing() {
        Mockito.when(fyersService.getHistoricalData(Mockito.anyString(), Mockito.anyInt(), Mockito.anyString()))
                .thenReturn(List.of());

        ScanRequest request = ScanRequest.builder()
                .universe(ScanRequest.Universe.CUSTOM)
                .symbols(List.of("SBIN", "RELIANCE"))
                .tf("5m")
                .regime(ScanRequest.Regime.BULL)
                .dryRun(true)
                .build();

        ScanResponse response = manualScanService.runManualScan(42L, request);

        assertThat(response.getDiagnostics().getTotalSymbols()).isZero();
        assertThat(response.getDiagnostics().getFinalSignals()).isZero();
        assertThat(response.getDiagnostics().getRejectedStage1ReasonCounts())
                .containsKey("EMPTY_UNIVERSE");
    }

    private List<Candle> sampleCandles() {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                Candle.builder().open(100).high(101).low(99).close(100).volume(1000).timestamp(now.minusMinutes(10)).build(),
                Candle.builder().open(100).high(102).low(98).close(101).volume(1100).timestamp(now.minusMinutes(5)).build(),
                Candle.builder().open(101).high(103).low(100).close(102).volume(1200).timestamp(now).build()
        );
    }
}
