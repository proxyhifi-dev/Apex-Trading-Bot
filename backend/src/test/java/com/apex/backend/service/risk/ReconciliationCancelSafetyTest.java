package com.apex.backend.service.risk;

import com.apex.backend.repository.OrderIntentRepository;
import com.apex.backend.repository.PaperOrderRepository;
import com.apex.backend.repository.PaperPositionRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.service.BroadcastService;
import com.apex.backend.service.DecisionAuditService;
import com.apex.backend.service.SettingsService;
import com.apex.backend.service.SystemGuardService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ReconciliationCancelSafetyTest {

    @Test
    void cancelLoopSkipsNullAndContinuesOnErrors() {
        OrderIntentRepository orderIntentRepository = mock(OrderIntentRepository.class);
        TradeRepository tradeRepository = mock(TradeRepository.class);
        PaperOrderRepository paperOrderRepository = mock(PaperOrderRepository.class);
        PaperPositionRepository paperPositionRepository = mock(PaperPositionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        SettingsService settingsService = mock(SettingsService.class);
        SystemGuardService systemGuardService = mock(SystemGuardService.class);
        BroadcastService broadcastService = mock(BroadcastService.class);
        DecisionAuditService decisionAuditService = mock(DecisionAuditService.class);
        FyersBrokerPort fyersBrokerPort = mock(FyersBrokerPort.class);
        PaperBrokerPort paperBrokerPort = mock(PaperBrokerPort.class);

        ReconciliationService reconciliationService = new ReconciliationService(
                orderIntentRepository,
                tradeRepository,
                paperOrderRepository,
                paperPositionRepository,
                userRepository,
                settingsService,
                systemGuardService,
                broadcastService,
                decisionAuditService,
                fyersBrokerPort,
                paperBrokerPort
        );
        ReflectionTestUtils.setField(reconciliationService, "autoCancelPendingOnMismatch", true);

        BrokerPort brokerPort = mock(BrokerPort.class);
        doThrow(new RuntimeException("boom")).when(brokerPort).cancelOrder(eq(1L), eq("order-1"));

        List<ReconciliationService.OrderSnapshot> orders = List.of(
                new ReconciliationService.OrderSnapshot(null, "SYM", "OPEN", 0, BigDecimal.ZERO, true),
                new ReconciliationService.OrderSnapshot(" ", "SYM", "OPEN", 0, BigDecimal.ZERO, true),
                new ReconciliationService.OrderSnapshot("order-1", "SYM", "OPEN", 0, BigDecimal.ZERO, true)
        );

        assertThatCode(() -> reconciliationService.handleMismatch(1L, brokerPort, orders)).doesNotThrowAnyException();
        verify(brokerPort, times(1)).cancelOrder(1L, "order-1");
    }
}
