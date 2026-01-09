package com.apex.backend.service;

import com.apex.backend.dto.SettingsDTO;
import com.apex.backend.model.Settings;
import com.apex.backend.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsRepository settingsRepository;

    @Transactional
    public Settings getOrCreateSettings(Long userId) {
        return settingsRepository.findByUserId(userId)
                .orElseGet(() -> settingsRepository.save(Settings.builder()
                        .userId(userId)
                        .build()));
    }

    public String getModeForUser(Long userId) {
        return getOrCreateSettings(userId).getMode();
    }

    public boolean isPaperModeForUser(Long userId) {
        return "paper".equalsIgnoreCase(getModeForUser(userId));
    }

    @Transactional
    public Settings updateSettings(Long userId, SettingsDTO request) {
        Settings settings = getOrCreateSettings(userId);
        if (request.getMode() != null && !request.getMode().isBlank()) {
            settings.setMode(request.getMode().toLowerCase());
        }
        if (request.getMaxPositions() != null) {
            settings.setMaxPositions(request.getMaxPositions());
        }
        if (request.getRiskLimits() != null) {
            if (request.getRiskLimits().getMaxRiskPerTradePercent() != null) {
                settings.setMaxRiskPerTradePercent(request.getRiskLimits().getMaxRiskPerTradePercent());
            }
            if (request.getRiskLimits().getMaxDailyLossPercent() != null) {
                settings.setMaxDailyLossPercent(request.getRiskLimits().getMaxDailyLossPercent());
            }
        }
        return settingsRepository.save(settings);
    }

}
