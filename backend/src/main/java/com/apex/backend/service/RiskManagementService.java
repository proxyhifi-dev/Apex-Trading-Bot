package com.apex.backend.service;

import com.apex.backend.dto.RiskStatusDto;
import com.apex.backend.model.OrderIntent;
import com.apex.backend.model.OrderState;
import com.apex.backend.model.Trade;
import com.apex.backend.model.User;
import com.apex.backend.repository.OrderIntentRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.service.risk.BrokerPort;
import com.apex.backend.service.risk.FyersBrokerPort;
import com.apex.backend.service.risk.PaperBrokerPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskManagementService {
    
    private final TradeRepository tradeRepository;
    private final PortfolioService portfolioService;
    private final PortfolioHeatService portfolioHeatService;
    private final SystemGuardService systemGuardService;
    private final EmergencyPanicService emergencyPanicService;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final FyersBrokerPort fyersBrokerPort;
    private final PaperBrokerPort paperBrokerPort;
    private final OrderIntentRepository orderIntentRepository;
    private final OrderStateMachine orderStateMachine;
    private final AuditEventService auditEventService;
    
    @Value("${apex.trading.capital:100000}")
    private double initialCapital;
    
    @Value("${apex.risk.daily-loss-limit:2000}")
    private double dailyLossLimit;

    @Value("${apex.risk.daily-max-loss:2000}")
    private double dailyMaxLoss;

    @Value("${apex.risk.max-consecutive-losses:3}")
    private int maxConsecutiveLosses;
    
    @Value("${apex.risk.max-position-size:10000}")
    private double maxPositionSize;

    @Value("${apex.risk.heat-max-pct:0.06}")
    private double maxHeatPct;

    @Value("${apex.risk.enforce-enabled:true}")
    private boolean enforceEnabled;
    
    /**
     * Get comprehensive risk status
     */
    public RiskStatusDto getRiskStatus() {
        try {
            double totalRealizedPnl = getTodaysPnL();
            double portfolioValue = portfolioService.getPortfolioValue(true);
            double availableEquity = portfolioService.getAvailableEquity(true);
            int openPositions = portfolioService.getOpenPositionCount(true);
            
            boolean riskExceeded = isRiskLimitExceeded();
            
            log.debug("Risk status calculated - PnL: {}, Portfolio: {}, Positions: {}", 
                totalRealizedPnl, portfolioValue, openPositions);
            
            return RiskStatusDto.builder()
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
            return RiskStatusDto.builder()
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

    @Scheduled(fixedDelayString = "${apex.risk.enforce-interval-ms:60000}")
    public void enforceRiskLimits() {
        if (!enforceEnabled) {
            return;
        }
        List<User> users = userRepository.findAll();
        for (User user : users) {
            Long userId = user.getId();
            if (userId == null) {
                continue;
            }
            boolean paper = settingsService.isPaperModeForUser(userId);
            double dailyPnl = getTodaysPnL(userId, paper);
            if (dailyPnl < -dailyMaxLoss) {
                log.error("Daily loss breached for user {} pnl={} limit={}", userId, dailyPnl, dailyMaxLoss);
                auditEventService.recordEvent(userId, "risk_event", "DAILY_LOSS_BREACH",
                        "Daily loss breached",
                        java.util.Map.of("pnl", dailyPnl, "limit", dailyMaxLoss));
                emergencyPanicService.triggerGlobalEmergency("DAILY_LOSS_BREACH");
                return;
            }
            int consecutiveLosses = countConsecutiveLosses(userId, paper);
            if (consecutiveLosses >= maxConsecutiveLosses) {
                log.error("Consecutive loss limit breached for user {} losses={} max={}", userId, consecutiveLosses, maxConsecutiveLosses);
                auditEventService.recordEvent(userId, "risk_event", "CONSECUTIVE_LOSS_BREACH",
                        "Consecutive loss limit breached",
                        java.util.Map.of("losses", consecutiveLosses, "max", maxConsecutiveLosses));
                emergencyPanicService.triggerGlobalEmergency("CONSECUTIVE_LOSS_BREACH");
                return;
            }
            double equity = portfolioService.getAvailableEquity(paper, userId);
            double heat = portfolioHeatService.currentPortfolioHeat(userId, java.math.BigDecimal.valueOf(equity));
            if (heat > maxHeatPct) {
                log.warn("Portfolio heat breached for user {} heat={} max={}", userId, heat, maxHeatPct);
                systemGuardService.setSafeMode(true, "PORTFOLIO_HEAT", java.time.Instant.now());
                cancelPendingOrders(userId, paper);
            }
        }
    }
    
    /**
     * Get today's P&L
     */
    public double getTodaysPnL() {
        try {
            return getTodaysPnL(null, null);
        } catch (Exception e) {
            log.error("Failed to calculate today's PnL", e);
            return 0;
        }
    }

    private double getTodaysPnL(Long userId, Boolean paper) {
        try {
            List<Trade> trades = tradeRepository.findAll();
            List<Trade> closedTradesToday = trades.stream()
                .filter(t -> t.getStatus() == Trade.TradeStatus.CLOSED)
                .filter(t -> t.getExitTime() != null &&
                    t.getExitTime().toLocalDate().equals(LocalDateTime.now().toLocalDate()))
                .filter(t -> userId == null || userId.equals(t.getUserId()))
                .filter(t -> paper == null || t.isPaperTrade() == paper)
                .toList();

            return closedTradesToday.stream()
                .mapToDouble(t -> t.getRealizedPnl() != null ? t.getRealizedPnl().doubleValue() : 0)
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
                    runningBalance += trade.getRealizedPnl().doubleValue();
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
            if (!isPositionSizeValid(trade.getQuantity() * trade.getEntryPrice().doubleValue())) {
                log.warn("Trade position size validation failed");
                return false;
            }
            
            // Check daily loss limit
            if (isRiskLimitExceeded()) {
                log.warn("Risk limit exceeded, trade rejected");
                return false;
            }
            
            // Check available equity
            double requiredCapital = trade.getQuantity() * trade.getEntryPrice().doubleValue();
            double availableEquity = portfolioService.getAvailableEquity(trade.isPaperTrade(), trade.getUserId());
            
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

    private void cancelPendingOrders(Long userId, boolean paperMode) {
        BrokerPort brokerPort = paperMode ? paperBrokerPort : fyersBrokerPort;
        List<String> failed = new ArrayList<>();
        for (BrokerPort.BrokerOrder order : brokerPort.openOrders(userId)) {
            try {
                brokerPort.cancelOrder(userId, order.orderId());
            } catch (Exception ex) {
                failed.add(order.orderId());
            }
        }
        if (!paperMode) {
            List<OrderIntent> intents = orderIntentRepository.findByUserIdAndOrderStateIn(
                    userId,
                    List.of(OrderState.CREATED, OrderState.SENT, OrderState.ACKED, OrderState.PART_FILLED, OrderState.CANCEL_REQUESTED)
            );
            for (OrderIntent intent : intents) {
                if (intent.getOrderState() != OrderState.CANCEL_REQUESTED) {
                    orderStateMachine.transition(intent, OrderState.CANCEL_REQUESTED, "RISK_HEAT_CANCEL");
                }
            }
        }
        if (!failed.isEmpty()) {
            log.warn("Failed to cancel some orders on risk heat: userId={} orders={}", userId, failed);
        }
    }

    private int countConsecutiveLosses(Long userId, boolean paperMode) {
        List<Trade> closedTrades = tradeRepository.findAll().stream()
                .filter(t -> t.getStatus() == Trade.TradeStatus.CLOSED)
                .filter(t -> userId.equals(t.getUserId()))
                .filter(t -> t.isPaperTrade() == paperMode)
                .sorted((a, b) -> {
                    if (a.getExitTime() == null && b.getExitTime() == null) {
                        return 0;
                    }
                    if (a.getExitTime() == null) {
                        return 1;
                    }
                    if (b.getExitTime() == null) {
                        return -1;
                    }
                    return b.getExitTime().compareTo(a.getExitTime());
                })
                .toList();
        int count = 0;
        for (Trade trade : closedTrades) {
            if (trade.getRealizedPnl() != null && trade.getRealizedPnl().doubleValue() < 0) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }
}
