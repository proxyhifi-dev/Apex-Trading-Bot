package com.apex.backend.service;

import com.apex.backend.model.Trade;
import com.apex.backend.model.User;
import com.apex.backend.repository.OrderIntentRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.service.risk.FyersBrokerPort;
import com.apex.backend.service.risk.PaperBrokerPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskManagementServiceTest {

    @Test
    void dailyLossBreachTriggersEmergency() {
        TradeRepository tradeRepository = mock(TradeRepository.class);
        PortfolioService portfolioService = mock(PortfolioService.class);
        PortfolioHeatService portfolioHeatService = mock(PortfolioHeatService.class);
        SystemGuardService systemGuardService = mock(SystemGuardService.class);
        EmergencyPanicService emergencyPanicService = mock(EmergencyPanicService.class);
        UserRepository userRepository = mock(UserRepository.class);
        SettingsService settingsService = mock(SettingsService.class);
        FyersBrokerPort fyersBrokerPort = mock(FyersBrokerPort.class);
        PaperBrokerPort paperBrokerPort = mock(PaperBrokerPort.class);
        OrderIntentRepository orderIntentRepository = mock(OrderIntentRepository.class);

        User user = User.builder().id(1L).username("u").passwordHash("x").build();
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(settingsService.isPaperModeForUser(1L)).thenReturn(false);
        when(tradeRepository.findAll()).thenReturn(List.of(
                Trade.builder()
                        .id(10L)
                        .userId(1L)
                        .status(Trade.TradeStatus.CLOSED)
                        .exitTime(LocalDateTime.now())
                        .realizedPnl(BigDecimal.valueOf(-5000))
                        .build()
        ));

        RiskManagementService service = new RiskManagementService(
                tradeRepository,
                portfolioService,
                portfolioHeatService,
                systemGuardService,
                emergencyPanicService,
                userRepository,
                settingsService,
                fyersBrokerPort,
                paperBrokerPort,
                orderIntentRepository
        );
        setField(service, "dailyLossLimit", 1000.0);
        setField(service, "enforceEnabled", true);

        service.enforceRiskLimits();

        verify(emergencyPanicService).triggerGlobalEmergency(eq("DAILY_LOSS_BREACH"));
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
