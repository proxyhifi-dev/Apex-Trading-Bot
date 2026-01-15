package com.apex.backend.service.risk;

import com.apex.backend.entity.SystemGuardState;
import com.apex.backend.model.OrderIntent;
import com.apex.backend.model.OrderState;
import com.apex.backend.model.Trade;
import com.apex.backend.model.User;
import com.apex.backend.repository.OrderIntentRepository;
import com.apex.backend.repository.PaperOrderRepository;
import com.apex.backend.repository.PaperPositionRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.service.BroadcastService;
import com.apex.backend.service.DecisionAuditService;
import com.apex.backend.service.SettingsService;
import com.apex.backend.service.SystemGuardService;
import com.apex.backend.util.MoneyUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconciliationServiceTest {

    @Test
    void matchedStateDoesNotEnterSafeMode() {
        ReconciliationService service = buildService(false, List.of(
                new BrokerPort.BrokerOrder("BRK1", "NSE:ABC", "OPEN", 0, MoneyUtils.bd(100))
        ), List.of(new BrokerPort.BrokerPosition("NSE:ABC", 10, MoneyUtils.bd(100))));

        ReconciliationService.ReconcileReport report = service.reconcile();

        assertThat(report.mismatch()).isFalse();
    }

    @Test
    void orderMissingTriggersSafeMode() {
        SystemGuardService guardService = mock(SystemGuardService.class);
        ReconciliationService service = buildService(false, List.of(), List.of(
                new BrokerPort.BrokerPosition("NSE:ABC", 10, MoneyUtils.bd(100))
        ), guardService);

        ReconciliationService.ReconcileReport report = service.reconcile();

        assertThat(report.mismatch()).isTrue();
        verify(guardService).setSafeMode(eq(true), any(), any());
    }

    @Test
    void positionMismatchTriggersSafeMode() {
        SystemGuardService guardService = mock(SystemGuardService.class);
        ReconciliationService service = buildService(false, List.of(
                new BrokerPort.BrokerOrder("BRK1", "NSE:ABC", "OPEN", 0, MoneyUtils.bd(100))
        ), List.of(new BrokerPort.BrokerPosition("NSE:ABC", 5, MoneyUtils.bd(100))), guardService);

        ReconciliationService.ReconcileReport report = service.reconcile();

        assertThat(report.mismatch()).isTrue();
        verify(guardService).setSafeMode(eq(true), any(), any());
    }

    private ReconciliationService buildService(boolean paperMode, List<BrokerPort.BrokerOrder> orders, List<BrokerPort.BrokerPosition> positions) {
        return buildService(paperMode, orders, positions, mock(SystemGuardService.class));
    }

    private ReconciliationService buildService(boolean paperMode, List<BrokerPort.BrokerOrder> orders, List<BrokerPort.BrokerPosition> positions, SystemGuardService guardService) {
        OrderIntentRepository orderIntentRepository = mock(OrderIntentRepository.class);
        TradeRepository tradeRepository = mock(TradeRepository.class);
        PaperOrderRepository paperOrderRepository = mock(PaperOrderRepository.class);
        PaperPositionRepository paperPositionRepository = mock(PaperPositionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        SettingsService settingsService = mock(SettingsService.class);
        BroadcastService broadcastService = mock(BroadcastService.class);
        DecisionAuditService decisionAuditService = mock(DecisionAuditService.class);
        FyersBrokerPort fyersBrokerPort = mock(FyersBrokerPort.class);
        PaperBrokerPort paperBrokerPort = mock(PaperBrokerPort.class);

        when(userRepository.findAll()).thenReturn(List.of(User.builder().id(1L).username("user").passwordHash("x").build()));
        when(settingsService.isPaperModeForUser(1L)).thenReturn(paperMode);
        when(orderIntentRepository.findByUserIdAndOrderStateIn(eq(1L), any())).thenReturn(List.of(sampleOrderIntent()));
        when(tradeRepository.findByUserIdAndStatus(1L, Trade.TradeStatus.OPEN)).thenReturn(List.of(sampleTrade()));
        when(fyersBrokerPort.openOrders(1L)).thenReturn(orders);
        when(fyersBrokerPort.openPositions(1L)).thenReturn(positions);

        SystemGuardState state = SystemGuardState.builder().id(1L).safeMode(false).updatedAt(Instant.now()).build();
        when(guardService.updateReconcileTimestamp(any())).thenReturn(state);
        when(guardService.setSafeMode(eq(true), any(), any())).thenReturn(state);

        ReconciliationService service = new ReconciliationService(
                orderIntentRepository,
                tradeRepository,
                paperOrderRepository,
                paperPositionRepository,
                userRepository,
                settingsService,
                guardService,
                broadcastService,
                decisionAuditService,
                fyersBrokerPort,
                paperBrokerPort
        );
        setField(service, "enabled", true);
        setField(service, "safeModeOnMismatch", true);
        setField(service, "qtyTolerance", 1);
        setField(service, "priceTolerancePct", 0.25);
        setField(service, "autoCancelPendingOnMismatch", true);
        setField(service, "autoFlattenOnMismatch", false);
        return service;
    }

    private OrderIntent sampleOrderIntent() {
        return OrderIntent.builder()
                .clientOrderId("CLIENT1")
                .brokerOrderId("BRK1")
                .userId(1L)
                .symbol("NSE:ABC")
                .side("BUY")
                .quantity(10)
                .status("SENT")
                .orderState(OrderState.SENT)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Trade sampleTrade() {
        return Trade.builder()
                .id(1L)
                .userId(1L)
                .symbol("NSE:ABC")
                .quantity(10)
                .entryPrice(MoneyUtils.bd(100))
                .entryTime(LocalDateTime.now())
                .status(Trade.TradeStatus.OPEN)
                .build();
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
