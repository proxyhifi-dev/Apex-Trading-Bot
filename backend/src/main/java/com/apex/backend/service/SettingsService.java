package com.apex.backend.service;

import com.apex.backend.dto.SettingsDTO;
import com.apex.backend.exception.BadRequestException;
import com.apex.backend.model.Settings;
import com.apex.backend.model.TradingMode;
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

    public TradingMode getTradingMode(Long userId) {
        return TradingMode.fromStored(getOrCreateSettings(userId).getMode());
    }

    public boolean isPaperModeForUser(Long userId) {
        return getTradingMode(userId) == TradingMode.PAPER;
    }

    @Transactional
    public Settings updateSettings(Long userId, SettingsDTO request) {
        Settings settings = getOrCreateSettings(userId);
        if (request.getMode() != null && !request.getMode().isBlank()) {
            try {
                TradingMode mode = TradingMode.fromRequest(request.getMode());
                settings.setMode(mode.name());
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Mode must be PAPER or LIVE");
            }
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
