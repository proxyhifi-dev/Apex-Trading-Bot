package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class BotStrategy {

    private final ScannerOrchestrator orchestrator;
    private final StrategyConfig config;

    // ✅ NO @Scheduled here. BotScheduler handles the timing.
    // This allows manual triggering via Controller if needed.
    public void executeScan(boolean force) {
        Long ownerUserId = config.getTrading().getOwnerUserId();
        if (ownerUserId == null) {
            log.warn("⚠️ Skipping scan because apex.trading.owner-user-id is not configured.");
            return;
        }
        orchestrator.runScanner(ownerUserId);
    }
}
