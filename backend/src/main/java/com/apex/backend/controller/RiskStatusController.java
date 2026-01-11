package com.apex.backend.controller;

import com.apex.backend.dto.RiskStatusSummaryDto;
import com.apex.backend.service.MetricsService;
import com.apex.backend.service.PortfolioHeatService;
import com.apex.backend.service.PortfolioService;
import com.apex.backend.service.RiskManagementEngine;
import com.apex.backend.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/risk-status")
@RequiredArgsConstructor
public class RiskStatusController {

    private final RiskManagementEngine riskManagementEngine;
    private final PortfolioHeatService portfolioHeatService;
    private final PortfolioService portfolioService;
    private final SettingsService settingsService;
    private final MetricsService metricsService;

    @GetMapping("/status")
    public RiskStatusSummaryDto status(@RequestParam Long userId) {
        boolean isPaper = settingsService.isPaperModeForUser(userId);
        BigDecimal equity = BigDecimal.valueOf(portfolioService.getAvailableEquity(isPaper, userId));
        double heat = portfolioHeatService.currentPortfolioHeat(userId, equity);

        metricsService.updateRiskUsage(heat);

        return new RiskStatusSummaryDto(
                riskManagementEngine.isTradingHalted(equity.doubleValue()),
                heat,
                (int) riskManagementEngine.getOpenPositionCount(),
                riskManagementEngine.getConsecutiveLosses()
        );
    }
}
