package com.apex.backend.service;

import com.apex.backend.model.ExitRetryRequest;
import com.apex.backend.model.PositionState;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.ExitRetryRepository;
import com.apex.backend.repository.TradeRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExitRetryServiceTest {

    @Test
    void failedExitIsQueuedAndLoggedToDlqAfterMaxAttempts() {
        ExitRetryRepository exitRetryRepository = mock(ExitRetryRepository.class);
        TradeRepository tradeRepository = mock(TradeRepository.class);
        ExecutionEngine executionEngine = mock(ExecutionEngine.class);
        TradeCloseService tradeCloseService = mock(TradeCloseService.class);
        DeadLetterQueueService deadLetterQueueService = mock(DeadLetterQueueService.class);

        ExitRetryService service = new ExitRetryService(
                exitRetryRepository,
                tradeRepository,
                executionEngine,
                tradeCloseService,
                deadLetterQueueService
        );
        setField(service, "maxAttempts", 1);
        setField(service, "retryDelaySeconds", 1);

        ExitRetryRequest request = ExitRetryRequest.builder()
                .id(1L)
                .tradeId(10L)
                .userId(1L)
                .symbol("NSE:ABC")
                .quantity(1)
                .side("SELL")
                .paper(false)
                .attempts(0)
                .resolved(false)
                .nextAttemptAt(Instant.now().minusSeconds(1))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Trade trade = Trade.builder()
                .id(10L)
                .userId(1L)
                .symbol("NSE:ABC")
                .quantity(1)
                .entryPrice(BigDecimal.ONE)
                .entryTime(LocalDateTime.now())
                .status(Trade.TradeStatus.OPEN)
                .positionState(PositionState.OPEN)
                .build();

        when(exitRetryRepository.findByResolvedFalseAndNextAttemptAtBefore(any())).thenReturn(List.of(request));
        when(tradeRepository.findById(10L)).thenReturn(Optional.of(trade));
        when(executionEngine.execute(any())).thenThrow(new RuntimeException("timeout"));

        service.processQueue();

        verify(deadLetterQueueService).logFailure(eq("EXIT_RETRY"), eq("tradeId=10"), eq("timeout"));
        verify(exitRetryRepository).save(any(ExitRetryRequest.class));
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
