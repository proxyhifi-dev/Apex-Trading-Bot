package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class BotScheduler {

    private final StrategyConfig config;
    private final CircuitBreaker circuitBreaker;
    private final ScannerOrchestrator scannerOrchestrator;
    private final ExitManager exitManager;
    private final LogBroadcastService logger;

    @Scheduled(fixedDelayString = "${apex.scanner.interval}000")
    public void runBotCycle() {
        if (!isMarketOpen()) return;

        if (circuitBreaker.isGlobalHalt()) {
            log.warn("⛔ Circuit Breaker Active. Skipping.");
            return;
        }

        log.info("🔄 Bot Cycle...");
        exitManager.manageExits();
        scannerOrchestrator.runScanner();
    }

    public void forceScan() {
        log.info("⚡ Manual Scan Triggered");
        scannerOrchestrator.runScanner();
    }

    private boolean isMarketOpen() {
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(9, 15)) && now.isBefore(LocalTime.of(15, 30));
    }
}