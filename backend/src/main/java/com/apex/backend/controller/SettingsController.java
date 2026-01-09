package com.apex.backend.controller;

import com.apex.backend.dto.SettingsDTO;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.model.Settings;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public ResponseEntity<SettingsDTO> getSettings(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        Settings settings = settingsService.getOrCreateSettings(userId);
        SettingsDTO response = SettingsDTO.builder()
                .mode(settingsService.getTradingMode(userId).name())
                .maxPositions(settings.getMaxPositions())
                .riskLimits(SettingsDTO.RiskLimitsDTO.builder()
                        .maxRiskPerTradePercent(settings.getMaxRiskPerTradePercent())
                        .maxDailyLossPercent(settings.getMaxDailyLossPercent())
                        .build())
                .build();

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<SettingsDTO> updateSettings(@AuthenticationPrincipal UserPrincipal principal,
                                                      @RequestBody SettingsDTO request) {
        Long userId = requireUserId(principal);
        Settings settings = settingsService.updateSettings(userId, request);
        SettingsDTO response = SettingsDTO.builder()
                .mode(settingsService.getTradingMode(userId).name())
                .maxPositions(settings.getMaxPositions())
                .riskLimits(SettingsDTO.RiskLimitsDTO.builder()
                        .maxRiskPerTradePercent(settings.getMaxRiskPerTradePercent())
                        .maxDailyLossPercent(settings.getMaxDailyLossPercent())
                        .build())
                .build();
        return ResponseEntity.ok(response);
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }
}
