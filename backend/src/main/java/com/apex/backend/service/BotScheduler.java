package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.model.User;
import com.apex.backend.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class BotScheduler {
    
    private final StrategyConfig config;
    private final com.apex.backend.service.risk.CircuitBreakerService tradingGuardService;
    private final ScannerOrchestrator scannerOrchestrator;
    private final ExitManager exitManager;
    private final BotStatusService botStatusService;
    private final StrategyHealthService strategyHealthService;
    private final SystemGuardService systemGuardService;
    private final UserRepository userRepository;
    private final WatchlistService watchlistService;
    
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

    @PostConstruct
    public void logSchedulerMode() {
        if (!config.getScanner().isSchedulerEnabled()
                || config.getScanner().getMode() != StrategyConfig.Scanner.Mode.SCHEDULED) {
            log.info("Scanner scheduler disabled (manual-only mode)");
        }
    }
    
    /**
     * Main bot cycle - invoked by the scheduler when enabled.
     */
    @Scheduled(fixedDelayString = "${apex.scanner.interval}000")
    @ConditionalOnProperty(name = "apex.scanner.scheduler-enabled", havingValue = "true")
    public void runBotCycle() {
        try {
            if (!config.getScanner().isEnabled()
                    || !config.getScanner().isSchedulerEnabled()
                    || config.getScanner().getMode() != StrategyConfig.Scanner.Mode.SCHEDULED) {
                log.debug("Scanner disabled or in manual mode. Skipping scheduled scan cycle.");
                return;
            }
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

            if (systemGuardService.getState().isSafeMode()) {
                log.warn("⛔ System guard safe mode enabled. Skipping cycle.");
                botStatusService.markPaused("System guard safe mode");
                return;
            }

            log.info("🔄 Running Bot Cycle...");
            botStatusService.markRunning();
            botStatusService.setLastScanTime(LocalDateTime.now());
            List<User> users = userRepository.findAll().stream()
                    .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                    .toList();
            if (users.isEmpty()) {
                botStatusService.markPaused("No active users");
                return;
            }
            for (User user : users) {
                Long userId = user.getId();
                if (watchlistService.isWatchlistEmpty(userId)) {
                    continue;
                }
                var guardDecision = tradingGuardService.canTrade(userId, Instant.now());
                if (!guardDecision.allowed()) {
                    log.warn("⛔ Circuit breaker guard active for user {}. Skipping scan: {}", userId, guardDecision.reason());
                    continue;
                }
                var healthState = strategyHealthService.getLatestState(userId);
                if (healthState != null && healthState.isPaused()) {
                    continue;
                }
                exitManager.manageExits(userId);
                scannerOrchestrator.runScanner(userId);
            }
            botStatusService.setNextScanTime(LocalDateTime.now().plusSeconds(config.getScanner().getInterval()));
            log.info("✅ Bot Cycle Complete");
        } catch (Exception e) {
            log.error("❌ Error in bot cycle", e);
            botStatusService.setLastError(e.getMessage());
        }
    }

    /**
     * Backward-compatible entry point for scheduled scans.
     */
    public void runBotCycle() {
        runScheduledCycle();
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
        log.warn("⚠️ Skipping manual scan without user context. Use /api/signals/scan-now instead.");
    }
    
    /**
     * Check if market is open (NSE trading hours: 9:15 AM to 3:30 PM)
     */
    private boolean isMarketOpen() {
        LocalTime now = LocalTime.now();
        LocalTime open = LocalTime.parse(config.getScanner().getMarketOpen());
        LocalTime close = LocalTime.parse(config.getScanner().getMarketClose());
        return now.isAfter(open) && now.isBefore(close);
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
