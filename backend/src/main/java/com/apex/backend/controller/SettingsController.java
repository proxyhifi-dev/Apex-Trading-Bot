package com.apex.backend.controller;

import com.apex.backend.dto.SettingsDTO;
import com.apex.backend.model.Settings;
import com.apex.backend.security.JwtTokenProvider;
import com.apex.backend.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping
    public ResponseEntity<SettingsDTO> getSettings(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = resolveUserId(authHeader);
        Settings settings = settingsService.getOrCreateSettings(userId);
        SettingsDTO response = SettingsDTO.builder()
                .mode(settings.getMode())
                .maxPositions(settings.getMaxPositions())
                .riskLimits(SettingsDTO.RiskLimitsDTO.builder()
                        .maxRiskPerTradePercent(settings.getMaxRiskPerTradePercent())
                        .maxDailyLossPercent(settings.getMaxDailyLossPercent())
                        .build())
                .build();

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<SettingsDTO> updateSettings(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                      @RequestBody SettingsDTO request) {
        Long userId = resolveUserId(authHeader);
        Settings settings = settingsService.updateSettings(userId, request);
        SettingsDTO response = SettingsDTO.builder()
                .mode(settings.getMode())
                .maxPositions(settings.getMaxPositions())
                .riskLimits(SettingsDTO.RiskLimitsDTO.builder()
                        .maxRiskPerTradePercent(settings.getMaxRiskPerTradePercent())
                        .maxDailyLossPercent(settings.getMaxDailyLossPercent())
                        .build())
                .build();
        return ResponseEntity.ok(response);
    }

    private Long resolveUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalStateException("Missing Authorization header");
        }
        String jwt = authHeader.substring(7);
        Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
        if (userId == null) {
            throw new IllegalStateException("Invalid user token");
        }
        return userId;
    }
}
