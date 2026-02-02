package com.apex.backend.service;

import com.apex.backend.entity.SystemGuardState;
import com.apex.backend.model.Trade;
import com.apex.backend.model.User;
import com.apex.backend.repository.OrderIntentRepository;
import com.apex.backend.repository.PaperOrderRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.service.risk.BrokerPort;
import com.apex.backend.service.risk.FyersBrokerPort;
import com.apex.backend.service.risk.PaperBrokerPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmergencyPanicServiceTest {

    @Test
    void triggerGlobalEmergencyCancelsOrdersAndQueuesExits() {
        SystemGuardService systemGuardService = mock(SystemGuardService.class);
        UserRepository userRepository = mock(UserRepository.class);
        SettingsService settingsService = mock(SettingsService.class);
        FyersBrokerPort fyersBrokerPort = mock(FyersBrokerPort.class);
        PaperBrokerPort paperBrokerPort = mock(PaperBrokerPort.class);
        OrderIntentRepository orderIntentRepository = mock(OrderIntentRepository.class);
        PaperOrderRepository paperOrderRepository = mock(PaperOrderRepository.class);
        TradeRepository tradeRepository = mock(TradeRepository.class);
        EmergencyExitExecutor emergencyExitExecutor = mock(EmergencyExitExecutor.class);
        RiskEventService riskEventService = mock(RiskEventService.class);
        AuditEventService auditEventService = mock(AuditEventService.class);
        OrderStateMachine orderStateMachine = mock(OrderStateMachine.class);

        User user = User.builder().id(1L).username("u").passwordHash("x").build();
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(settingsService.isPaperModeForUser(1L)).thenReturn(false);
        when(fyersBrokerPort.openOrders(1L)).thenReturn(List.of(
                new BrokerPort.BrokerOrder("BRK1", "NSE:ABC", "OPEN", 0, null)
        ));
        when(tradeRepository.findByUserIdAndStatus(1L, Trade.TradeStatus.OPEN)).thenReturn(List.of(
                Trade.builder().id(10L).userId(1L).symbol("NSE:ABC").quantity(1).entryPrice(java.math.BigDecimal.ONE)
                        .entryTime(java.time.LocalDateTime.now()).status(Trade.TradeStatus.OPEN).build()
        ));
        when(systemGuardService.setPanicMode(eq(true), eq("MANUAL_TRIGGER"), any(Instant.class)))
                .thenReturn(SystemGuardState.builder().id(1L).panicMode(true).build());

        EmergencyPanicService service = new EmergencyPanicService(
                systemGuardService,
                userRepository,
                settingsService,
                fyersBrokerPort,
                paperBrokerPort,
                orderIntentRepository,
                paperOrderRepository,
                tradeRepository,
                emergencyExitExecutor,
                riskEventService,
                auditEventService,
                orderStateMachine
        );

        service.triggerGlobalEmergency("MANUAL_TRIGGER");

        verify(fyersBrokerPort).cancelOrder(1L, "BRK1");
        verify(emergencyExitExecutor).enqueueExitAndAttempt(any(Trade.class), eq("EMERGENCY_PANIC"));
        verify(userRepository).saveAll(any());
        verify(systemGuardService).setPanicMode(eq(true), eq("MANUAL_TRIGGER"), any(Instant.class));
    }
}
