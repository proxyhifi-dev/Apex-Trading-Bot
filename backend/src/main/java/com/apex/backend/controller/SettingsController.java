package com.apex.backend.controller;

import com.apex.backend.dto.SettingsDTO;
import com.apex.backend.model.Settings;
import com.apex.backend.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsRepository settingsRepository;

    @GetMapping
    public ResponseEntity<SettingsDTO> getSettings() {
        Settings settings = settingsRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> settingsRepository.save(Settings.builder().build()));

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
}
