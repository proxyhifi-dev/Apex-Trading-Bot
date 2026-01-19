package com.apex.backend.integration;

import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.model.Trade;
import com.apex.backend.model.User;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.service.TradeExecutionService;
import com.apex.backend.trading.pipeline.DecisionResult;
import com.apex.backend.trading.pipeline.RiskDecision;
import com.apex.backend.trading.pipeline.SignalScore;
import com.apex.backend.trading.pipeline.TradeDecisionPipelineService;
import com.apex.backend.util.MoneyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Testcontainers
@ExtendWith(SpringExtension.class)
@SpringBootTest
class TradeExecutionE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("apex_trading_db")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TradeExecutionService tradeExecutionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockScreeningResultRepository screeningRepo;

    @Autowired
    private TradeRepository tradeRepository;

    @MockBean
    private TradeDecisionPipelineService tradeDecisionPipelineService;

    @Test
    void orderExecutionCreatesTradeAndStopsInPaperMode() {
        User user = userRepository.save(User.builder()
                .username("trade_user")
                .passwordHash("hash")
                .email("trade@example.com")
                .availableFunds(MoneyUtils.bd(100000.0))
                .totalInvested(MoneyUtils.ZERO)
                .currentValue(MoneyUtils.bd(100000.0))
                .createdAt(LocalDateTime.now())
                .build());

        SignalScore score = new SignalScore(true, 80.0, "A", 100.0, 95.0, "ok", null, List.of());
        RiskDecision riskDecision = new RiskDecision(true, 0.5, List.of(), 1.0, 1);
        DecisionResult decisionResult = new DecisionResult(
                "NSE:INFY-EQ",
                DecisionResult.DecisionAction.BUY,
                80.0,
                List.of("ok"),
                riskDecision,
                null,
                score,
                null
        );
        when(tradeDecisionPipelineService.evaluate(any())).thenReturn(decisionResult);

        StockScreeningResult signal = screeningRepo.save(StockScreeningResult.builder()
                .userId(user.getId())
                .symbol("NSE:INFY-EQ")
                .signalScore(80)
                .grade("A")
                .entryPrice(MoneyUtils.bd(100.0))
                .stopLoss(MoneyUtils.bd(95.0))
                .scanTime(LocalDateTime.now())
                .approvalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                .analysisReason("ok")
                .build());

        tradeExecutionService.approveAndExecute(user.getId(), signal.getId(), true, 15.0);

        List<Trade> trades = tradeRepository.findByUserIdAndStatus(user.getId(), Trade.TradeStatus.OPEN);
        assertThat(trades).hasSize(1);
        Trade trade = trades.get(0);
        assertThat(trade.getStopOrderId()).isNotBlank();
        assertThat(trade.getStopOrderState()).isNotNull();
        assertThat(trade.getPositionState()).isEqualTo(com.apex.backend.model.PositionState.OPEN);
    }
}
