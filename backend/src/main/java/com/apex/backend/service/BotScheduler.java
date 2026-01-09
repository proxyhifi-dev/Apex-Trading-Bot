package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class BotScheduler {
    
    private final StrategyConfig config;
    private final CircuitBreakerService circuitBreakerService;
    private final ScannerOrchestrator scannerOrchestrator;
    private final ExitManager exitManager;
    private final LogBroadcastService logger;
    private final BotStatusService botStatusService;
    
    // Market data connection status
    private final AtomicBoolean marketDataConnected = new AtomicBoolean(true);
    private final AtomicBoolean botReady = new AtomicBoolean(false);
    
    /**
     * Initialize bot on startup
     */
    public void initialize() {
        try {
            log.info("🚀 Initializing Apex Trading Bot...");
            marketDataConnected.set(true);
            botReady.set(true);
            botStatusService.markRunning();
            log.info("✅ Bot Initialization Complete - Ready for trading");
        } catch (Exception e) {
            log.error("❌ Failed to initialize bot", e);
            botReady.set(false);
            botStatusService.markStopped(e.getMessage());
        }
    }
    
    /**
     * Main bot cycle - runs periodically
     */
    @Scheduled(fixedDelayString = "${apex.scanner.interval}000")
    public void runBotCycle() {
        if (!botReady.get()) {
            log.warn("⏳ Bot not ready yet");
            botStatusService.markStopped("Bot not ready");
            return;
        }

        if (!isMarketOpen()) {
            log.debug("Market closed - skipping cycle");
            botStatusService.markPaused("Market closed");
            return;
        }
        
        if (circuitBreakerService.isGlobalHalt()) {
            log.warn("⛔ Circuit Breaker Active. Skipping cycle.");
            botStatusService.markPaused("Circuit breaker active");
            return;
        }

        try {
            log.info("🔄 Running Bot Cycle...");
            botStatusService.markRunning();
            botStatusService.setLastScanTime(LocalDateTime.now());
            Long ownerUserId = config.getTrading().getOwnerUserId();
            if (ownerUserId == null) {
                log.warn("⚠️ Skipping bot cycle because apex.trading.owner-user-id is not configured.");
                botStatusService.markPaused("Owner user not configured");
                return;
            }
            exitManager.manageExits(ownerUserId);
            scannerOrchestrator.runScanner(ownerUserId);
            botStatusService.setNextScanTime(LocalDateTime.now().plusSeconds(config.getScanner().getInterval()));
            log.info("✅ Bot Cycle Complete");
        } catch (Exception e) {
            log.error("❌ Error in bot cycle", e);
            botStatusService.setLastError(e.getMessage());
            // Update metrics to trigger circuit breaker if needed
            circuitBreakerService.updateMetrics();
        }
    }
    
    /**
     * Manual scan triggered by frontend
     */
    public void forceScan(Long userId) {
        if (!botReady.get()) {
            log.warn("⏳ Bot not yet initialized - initializing now");
            initialize();
        }

        try {
            log.info("⚡ Manual Scan Triggered by User");
            botStatusService.markRunning();
            botStatusService.setLastScanTime(LocalDateTime.now());
            scannerOrchestrator.runScanner(userId);
            botStatusService.setNextScanTime(LocalDateTime.now().plusSeconds(config.getScanner().getInterval()));
            log.info("✅ Manual scan completed");
        } catch (Exception e) {
            log.error("❌ Failed to execute manual scan", e);
            botStatusService.setLastError(e.getMessage());
        }
    }

    public void forceScan() {
        Long ownerUserId = config.getTrading().getOwnerUserId();
        if (ownerUserId == null) {
            log.warn("⚠️ Skipping manual scan because apex.trading.owner-user-id is not configured.");
            return;
        }
        forceScan(ownerUserId);
    }
    
    /**
     * Check if market is open (NSE trading hours: 9:15 AM to 3:30 PM)
     */
    private boolean isMarketOpen() {
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(9, 15)) && now.isBefore(LocalTime.of(15, 30));
    }
    
    /**
     * Get bot ready status
     */
    public boolean isBotReady() {
        return botReady.get();
    }
    
    /**
     * Get market data connection status
     */
    public boolean isMarketDataConnected() {
        return marketDataConnected.get();
    }
    
    /**
     * Set market data connection status
     */
    public void setMarketDataConnected(boolean connected) {
        marketDataConnected.set(connected);
        if (connected) {
            log.info("✅ Market data feed connected");
        } else {
            log.warn("⚠️ Market data feed disconnected");
        }
    }
}
