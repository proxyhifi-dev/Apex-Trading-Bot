package com.apex.backend.service;

import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CircuitBreaker {

    private final PortfolioService portfolioService;
    private final TradeRepository tradeRepository;

    @Value("${apex.risk.min-equity:10000}")
    private double minEquity;

    @Value("${apex.risk.daily-loss-limit-pct:0.05}")
    private double dailyLossLimit;

    @Value("${apex.risk.max-consecutive-losses:3}")
    private int maxConsecutiveLosses;

    public boolean isCircuitBreakerTriggered() {
        try {
            double currentEquity = portfolioService.getAvailableEquity(false);

            if (currentEquity < minEquity) {
                log.warn("Circuit breaker triggered: Equity {} below minimum {}", currentEquity, minEquity);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("Failed to check circuit breaker", e);
            return false;
        }
    }

    public void checkRiskLimits() {
        try {
            log.info("Checking risk limits");

            if (isCircuitBreakerTriggered()) {
                log.warn("CIRCUIT BREAKER TRIGGERED - STOP ALL TRADING");
            }
        } catch (Exception e) {
            log.error("Failed to check risk limits", e);
        }

    
        /**
     * Check if circuit breaker allows trading
     */
    public boolean canTrade() {
        return !isCircuitBreakerTriggered();
    }
    
    /**
     * Update risk metrics and circuit breaker status
     */
    public void updateMetrics() {
        try {
            log.info("Updating circuit breaker metrics");
            checkRiskLimits();
            log.debug("Circuit breaker metrics updated");
        } catch (Exception e) {
            log.error("Failed to update metrics", e);
        }
    }}
}
