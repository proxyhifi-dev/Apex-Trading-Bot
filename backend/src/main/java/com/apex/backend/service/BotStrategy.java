package com.apex.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class BotStrategy {

    private final ScannerOrchestrator orchestrator;

    // ✅ NO @Scheduled here. BotScheduler handles the timing.
    // This allows manual triggering via Controller if needed.
    public void executeScan(boolean force) {
        orchestrator.runScanner();
    }
}