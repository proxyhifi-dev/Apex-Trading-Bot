package com.apex.backend.service;

import com.apex.backend.dto.RiskLimitsResponse;
import com.apex.backend.dto.RiskLimitsUpdateRequest;
import com.apex.backend.exception.BadRequestException;
import com.apex.backend.model.RiskLimits;
import com.apex.backend.repository.RiskLimitsRepository;
import com.apex.backend.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class RiskLimitService {

    private final RiskLimitsRepository riskLimitsRepository;
    private final RiskEventService riskEventService;

    @Transactional
    public RiskLimitsResponse getLimits(Long userId) {
        RiskLimits limits = getOrCreate(userId);
        return map(limits);
    }

    @Transactional
    public RiskLimitsResponse updateLimits(Long userId, RiskLimitsUpdateRequest request) {
        RiskLimits limits = getOrCreate(userId);
        if (request.getDailyLossLimit() != null && request.getDailyLossLimit().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Daily loss limit must be non-negative");
        }
        if (request.getPortfolioHeatLimit() != null && request.getPortfolioHeatLimit().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Portfolio heat limit must be non-negative");
        }
        if (request.getMaxNotionalExposure() != null && request.getMaxNotionalExposure().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Max notional exposure must be non-negative");
        }
        if (request.getMaxSymbolExposure() != null && request.getMaxSymbolExposure().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Max symbol exposure must be non-negative");
        }
        if (request.getDailyLossLimit() != null) {
            limits.setDailyLossLimit(MoneyUtils.scale(request.getDailyLossLimit()));
        }
        if (request.getMaxPositions() != null) {
            limits.setMaxPositions(request.getMaxPositions());
        }
        if (request.getMaxConsecutiveLosses() != null) {
            limits.setMaxConsecutiveLosses(request.getMaxConsecutiveLosses());
        }
        if (request.getPortfolioHeatLimit() != null) {
            limits.setPortfolioHeatLimit(MoneyUtils.scale(request.getPortfolioHeatLimit()));
        }
        if (request.getMaxNotionalExposure() != null) {
            limits.setMaxNotionalExposure(MoneyUtils.scale(request.getMaxNotionalExposure()));
        }
        if (request.getMaxSymbolExposure() != null) {
            limits.setMaxSymbolExposure(MoneyUtils.scale(request.getMaxSymbolExposure()));
        }
        riskLimitsRepository.save(limits);
        return map(limits);
    }

    public boolean evaluateDailyLoss(Long userId, BigDecimal dailyPnl) {
        RiskLimits limits = getOrCreate(userId);
        if (limits.getDailyLossLimit() == null) {
            return true;
        }
        BigDecimal limit = limits.getDailyLossLimit();
        if (dailyPnl == null) {
            return true;
        }
        if (dailyPnl.compareTo(limit.negate()) <= 0) {
            riskEventService.record(userId, "DAILY_LOSS_LIMIT", "Daily loss limit breached", "pnl=" + dailyPnl);
            return false;
        }
        return true;
    }

    private RiskLimits getOrCreate(Long userId) {
        return riskLimitsRepository.findByUserId(userId)
                .orElseGet(() -> riskLimitsRepository.save(RiskLimits.builder()
                        .userId(userId)
                        .build()));
    }

    private RiskLimitsResponse map(RiskLimits limits) {
        return RiskLimitsResponse.builder()
                .dailyLossLimit(limits.getDailyLossLimit())
                .maxPositions(limits.getMaxPositions())
                .maxConsecutiveLosses(limits.getMaxConsecutiveLosses())
                .portfolioHeatLimit(limits.getPortfolioHeatLimit())
                .maxNotionalExposure(limits.getMaxNotionalExposure())
                .maxSymbolExposure(limits.getMaxSymbolExposure())
                .build();
    }
}
