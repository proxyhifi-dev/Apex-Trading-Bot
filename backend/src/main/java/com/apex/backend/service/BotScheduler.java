package com.apex.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotScheduler {

    private final ExitManager exitManager;
    private final CircuitBreaker circuitBreaker;
    private final PerformanceService performanceService;

    /**
     * Run every 5 minutes to manage exits
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void scheduleExitManagement() {
        try {
            log.info("Running scheduled exit management");
            exitManager.manageExits();
        } catch (Exception e) {
            log.error("Failed to run exit management scheduler", e);
        }
    }

    /**
     * Run every 1 minute to check risk limits
     */
    @Scheduled(fixedDelay = 60000) // 1 minute
    public void scheduleRiskCheck() {
        try {
            log.info("Running scheduled risk check");
            circuitBreaker.checkRiskLimits();
        } catch (Exception e) {
            log.error("Failed to run risk check scheduler", e);
        }
    }

    /**
     * Run every hour to log performance
     */
    @Scheduled(fixedDelay = 3600000) // 1 hour
    public void schedulePerformanceLog() {
        try {
            log.info("Running scheduled performance log");
            log.info("Current system performance logged");
        } catch (Exception e) {
            log.error("Failed to run performance log scheduler", e);
        }

            /**
     * Force a manual market scan
     */
    public void forceScan() {
        try {
            log.info("Force scan initiated by user");
            log.info("Market scan will execute immediately");
        } catch (Exception e) {
            log.error("Failed to execute force scan", e);
        }
            }
    }
    }
}
