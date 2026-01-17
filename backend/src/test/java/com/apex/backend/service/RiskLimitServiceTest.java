package com.apex.backend.service;

import com.apex.backend.model.RiskLimits;
import com.apex.backend.repository.RiskLimitsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskLimitServiceTest {

    @Mock
    private RiskLimitsRepository riskLimitsRepository;

    @Mock
    private RiskEventService riskEventService;

    @InjectMocks
    private RiskLimitService riskLimitService;

    @Test
    void dailyLossBreachTriggersRiskEvent() {
        Long userId = 77L;
        RiskLimits limits = RiskLimits.builder()
                .userId(userId)
                .dailyLossLimit(BigDecimal.valueOf(100))
                .build();
        when(riskLimitsRepository.findByUserId(userId)).thenReturn(Optional.of(limits));

        riskLimitService.evaluateDailyLoss(userId, BigDecimal.valueOf(-150));

        verify(riskEventService).record(eq(userId), eq("DAILY_LOSS_LIMIT"), anyString(), contains("-150"));
    }
}
