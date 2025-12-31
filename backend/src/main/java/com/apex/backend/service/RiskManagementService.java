package com.apex.backend.service;

import com.apex.backend.dto.RiskStatusDTO;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskManagementService {
    
    private final TradeRepository tradeRepository;
    private final PortfolioService portfolioService;
    
    @Value("${apex.trading.capital:100000}")
    private double initialCapital;
    
    @Value("${apex.risk.daily-loss-limit:2000}")
    private double dailyLossLimit;
    
    @Value("${apex.risk.max-position-size:10000}")
    private double maxPositionSize;
    
    /**
     * Get comprehensive risk status
     */
    public RiskStatusDTO getRiskStatus() {
        try {
            double totalRealizedPnl = getTodaysPnL();
            double portfolioValue = portfolioService.getPortfolioValue(true);
            double availableEquity = portfolioService.getAvailableEquity(true);
            int openPositions = portfolioService.getOpenPositionCount(true);
            
            boolean riskExceeded = isRiskLimitExceeded();
            
            log.debug("Risk status calculated - PnL: {}, Portfolio: {}, Positions: {}", 
                totalRealizedPnl, portfolioValue, openPositions);
            
            return RiskStatusDTO.builder()
                .dailyPnL(totalRealizedPnl)
                .dailyLossLimit(dailyLossLimit)
                .portfolioValue(portfolioValue)
                .availableEquity(availableEquity)
                .openPositions(openPositions)
                .riskExceeded(riskExceeded)
                .remainingDailyLoss(dailyLossLimit + totalRealizedPnl)
                .build();
        } catch (Exception e) {
            log.error("Failed to calculate risk status", e);
            return RiskStatusDTO.builder()
                .riskExceeded(true)
                .build();
        }
    }
    
    /**
     * Check if any risk limit is exceeded
     */
    public boolean isRiskLimitExceeded() {
        try {
            double todaysPnL = getTodaysPnL();
            
            // Check daily loss limit
            if (todaysPnL < -dailyLossLimit) {
                log.warn("Daily loss limit exceeded! Loss: {}, Limit: {}", todaysPnL, dailyLossLimit);
                return true;
            }
            
            // Check position count
            int openPositions = portfolioService.getOpenPositionCount(true);
            if (openPositions > 10) {
                log.warn("Too many open positions: {}", openPositions);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error checking risk limits", e);
            return true; // Default to risk exceeded on error
        }
    }
    
    /**
     * Get today's P&L
     */
    public double getTodaysPnL() {
        try {
            List<Trade> closedTradesToday = tradeRepository.findAll().stream()
                .filter(t -> t.getStatus() == Trade.TradeStatus.CLOSED)
                .filter(t -> t.getExitTime() != null && 
                    t.getExitTime().toLocalDate().equals(LocalDateTime.now().toLocalDate()))
                .toList();
            
            return closedTradesToday.stream()
                .mapToDouble(t -> t.getRealizedPnl() != null ? t.getRealizedPnl() : 0)
                .sum();
        } catch (Exception e) {
            log.error("Failed to calculate today's PnL", e);
            return 0;
        }
    }
    
    /**
     * Check if position size is within limits
     */
    public boolean isPositionSizeValid(double tradeSize) {
        if (tradeSize > maxPositionSize) {
            log.warn("Position size {} exceeds max allowed {}", tradeSize, maxPositionSize);
            return false;
        }
        return true;
    }
    
    /**
     * Set daily loss limit
     */
    public void setDailyLossLimit(double limit) {
        this.dailyLossLimit = limit;
        log.info("Daily loss limit set to: {}", limit);
    }
    
    /**
     * Get daily loss limit
     */
    public double getDailyLossLimit() {
        return dailyLossLimit;
    }
    
    /**
     * Set max position size
     */
    public void setMaxPositionSize(double maxSize) {
        this.maxPositionSize = maxSize;
        log.info("Max position size set to: {}", maxSize);
    }
    
    /**
     * Get max position size
     */
    public double getMaxPositionSize() {
        return maxPositionSize;
    }
    
    /**
     * Calculate max drawdown
     */
    public double calculateMaxDrawdown() {
        try {
            List<Trade> allTrades = tradeRepository.findAll();
            if (allTrades.isEmpty()) {
                return 0;
            }
            
            double maxDrawdown = 0;
            double peak = initialCapital;
            double runningBalance = initialCapital;
            
            for (Trade trade : allTrades) {
                if (trade.getRealizedPnl() != null) {
                    runningBalance += trade.getRealizedPnl();
                    if (runningBalance > peak) {
                        peak = runningBalance;
                    }
                    double currentDrawdown = ((peak - runningBalance) / peak) * 100;
                    maxDrawdown = Math.max(maxDrawdown, currentDrawdown);
                }
            }
            
            return Math.round(maxDrawdown * 100.0) / 100.0;
        } catch (Exception e) {
            log.error("Failed to calculate max drawdown", e);
            return 0;
        }
    }
    
    /**
     * Validate trade against risk rules
     */
    public boolean validateTrade(Trade trade) {
        try {
            // Check position size
            if (!isPositionSizeValid(trade.getQuantity() * trade.getEntryPrice())) {
                log.warn("Trade position size validation failed");
                return false;
            }
            
            // Check daily loss limit
            if (isRiskLimitExceeded()) {
                log.warn("Risk limit exceeded, trade rejected");
                return false;
            }
            
            // Check available equity
            double requiredCapital = trade.getQuantity() * trade.getEntryPrice();
            double availableEquity = portfolioService.getAvailableEquity(trade.isPaperTrade());
            
            if (availableEquity < requiredCapital) {
                log.warn("Insufficient equity for trade");
                return false;
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error validating trade", e);
            return false;
        }
    }
}
