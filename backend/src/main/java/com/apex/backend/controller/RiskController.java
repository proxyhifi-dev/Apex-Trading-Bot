package com.apex.backend.controller;

import com.apex.backend.dto.RiskStatusDTO;
import com.apex.backend.service.RiskManagementEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class RiskController {

    private final RiskManagementEngine riskEngine;

    @GetMapping("/status")
    public RiskStatusDTO getRiskStatus() {
        double assumedEquity = 100000.0;
        boolean tradingHalted = riskEngine.isTradingHalted(assumedEquity);
        boolean goodTradingTime = isWithinTradingHours();

        return RiskStatusDTO.builder()
                .tradingHalted(tradingHalted)
                .goodTradingTime(goodTradingTime)
                .reason(tradingHalted ? "Risk limits exceeded" : null)
                .currentDrawdown(null)
                .maxDrawdown(null)
                .consecutiveLosses(riskEngine.getConsecutiveLosses())
                .build();
    }

    private boolean isWithinTradingHours() {
        LocalTime now = LocalTime.now();
        LocalTime marketOpen = LocalTime.of(9, 15);
        LocalTime marketClose = LocalTime.of(15, 30);
        return now.isAfter(marketOpen) && now.isBefore(marketClose);
    }
}
