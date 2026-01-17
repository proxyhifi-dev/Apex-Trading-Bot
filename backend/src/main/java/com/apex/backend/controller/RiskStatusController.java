package com.apex.backend.controller;

import com.apex.backend.dto.RiskStatusSummaryDto;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.MetricsService;
import com.apex.backend.service.PortfolioHeatService;
import com.apex.backend.service.PortfolioService;
import com.apex.backend.service.RiskManagementEngine;
import com.apex.backend.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public RiskStatusSummaryDto status(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestParam(required = false) Long userId) {
        Long resolvedUserId = resolveUserId(principal, userId);
        boolean isPaper = settingsService.isPaperModeForUser(resolvedUserId);
        BigDecimal equity = BigDecimal.valueOf(portfolioService.getAvailableEquity(isPaper, resolvedUserId));
        double heat = portfolioHeatService.currentPortfolioHeat(resolvedUserId, equity);

        metricsService.updateRiskUsage(heat);

        return new RiskStatusSummaryDto(
                riskManagementEngine.isTradingHalted(equity.doubleValue()),
                heat,
                (int) riskManagementEngine.getOpenPositionCount(),
                riskManagementEngine.getConsecutiveLosses()
        );
    }

    private Long resolveUserId(UserPrincipal principal, Long userId) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        if (userId == null || userId.equals(principal.getUserId())) {
            return principal.getUserId();
        }
        if (principal.getRole() != null && principal.getRole().equalsIgnoreCase("ADMIN")) {
            return userId;
        }
        throw new org.springframework.security.access.AccessDeniedException("Insufficient permissions");
    }
}
