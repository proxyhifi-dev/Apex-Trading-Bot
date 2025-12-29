package com.apex.backend.controller;

import com.apex.backend.dto.RiskStatusDTO;
import com.apex.backend.service.CircuitBreaker;
import com.apex.backend.service.PortfolioService;
import com.apex.backend.service.RiskManagementEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class RiskController {

    private final RiskManagementEngine riskEngine;
    private final CircuitBreaker circuitBreaker;
    private final PortfolioService portfolioService;

    @GetMapping("/status")
    public RiskStatusDTO getRiskStatus() {
        double equity = portfolioService.getAvailableEquity(false); // Assume Live for monitoring

        // ✅ FIXED: Calls valid methods
        boolean dailyLimitHit = riskEngine.isTradingHalted(equity);
        boolean globalHalt = circuitBreaker.isGlobalHalt();
        boolean entryHalt = circuitBreaker.isEntryHalt();

        return RiskStatusDTO.builder()
                .isTradingHalted(dailyLimitHit || globalHalt)
                .isGlobalHalt(globalHalt)
                .isEntryHalt(entryHalt)
                .consecutiveLosses(riskEngine.getConsecutiveLosses())
                .dailyDrawdownPct(riskEngine.getDailyLossPercent(equity))
                .build();
    }
}