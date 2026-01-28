package com.apex.backend.controller;

import com.apex.backend.model.ScannerRun;
import com.apex.backend.repository.ScannerRunRepository;
import com.apex.backend.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=01234567890123456789012345678901"
})
@AutoConfigureMockMvc
class ScannerSummaryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScannerRunRepository scannerRunRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void latestSummaryReturnsStageCounts() throws Exception {
        Long userId = 42L;
        Map<String, Long> stageCounts = Map.of(
                "trend", 5L,
                "volume", 4L,
                "breakout", 3L,
                "rsi", 3L,
                "adx", 2L,
                "atr", 2L,
                "momentum", 2L,
                "squeeze", 1L,
                "finalSignals", 1L
        );

        ScannerRun run = ScannerRun.builder()
                .userId(userId)
                .status(ScannerRun.Status.COMPLETED)
                .universeType("WATCHLIST")
                .universePayload("{}")
                .dryRun(true)
                .mode("PAPER")
                .createdAt(Instant.now())
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .totalSymbols(5)
                .passedStage1(4)
                .passedStage2(2)
                .finalSignals(1)
                .stagePassCounts(objectMapper.writeValueAsString(stageCounts))
                .rejectedStage1ReasonCounts(objectMapper.writeValueAsString(Map.of()))
                .rejectedStage2ReasonCounts(objectMapper.writeValueAsString(Map.of()))
                .build();
        scannerRunRepository.save(run);

        String token = jwtTokenProvider.generateToken("tester", userId, "USER");

        mockMvc.perform(get("/api/scanner/latest-summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanId").value(run.getId()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.stagePassCounts.trend").value(5))
                .andExpect(jsonPath("$.stagePassCounts.finalSignals").value(1))
                .andExpect(jsonPath("$.signalsFound").value(1));
    }
}
