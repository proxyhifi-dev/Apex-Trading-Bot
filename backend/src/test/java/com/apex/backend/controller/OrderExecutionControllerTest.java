package com.apex.backend.controller;

import com.apex.backend.model.PaperAccount;
import com.apex.backend.model.Settings;
import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.model.TradingMode;
import com.apex.backend.model.User;
import com.apex.backend.repository.PaperAccountRepository;
import com.apex.backend.repository.PaperOrderRepository;
import com.apex.backend.repository.SettingsRepository;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.security.JwtTokenProvider;
import com.apex.backend.service.FyersService;
import com.apex.backend.util.MoneyUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SettingsRepository settingsRepository;

    @Autowired
    private PaperAccountRepository paperAccountRepository;

    @Autowired
    private PaperOrderRepository paperOrderRepository;

    @Autowired
    private StockScreeningResultRepository stockScreeningResultRepository;

    @MockBean
    private FyersService fyersService;

    private Long userId;
    private String token;

    @BeforeEach
    void setup() {
        stockScreeningResultRepository.deleteAll();
        paperOrderRepository.deleteAll();
        paperAccountRepository.deleteAll();
        settingsRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(User.builder()
                .username("testuser")
                .passwordHash("pass")
                .role("USER")
                .availableFunds(MoneyUtils.bd(100000))
                .totalInvested(MoneyUtils.ZERO)
                .currentValue(MoneyUtils.bd(100000))
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build());
        userId = user.getId();
        token = jwtTokenProvider.generateToken(user.getUsername(), userId, user.getRole());

        settingsRepository.save(Settings.builder()
                .userId(userId)
                .mode(TradingMode.PAPER.name())
                .build());

        paperAccountRepository.save(PaperAccount.builder()
                .userId(userId)
                .startingCapital(MoneyUtils.bd(100000))
                .cashBalance(MoneyUtils.bd(100000))
                .reservedMargin(MoneyUtils.ZERO)
                .realizedPnl(MoneyUtils.ZERO)
                .unrealizedPnl(MoneyUtils.ZERO)
                .updatedAt(LocalDateTime.now())
                .build());
    }

    @Test
    void placeModifyCancelOrderFlow() throws Exception {
        when(fyersService.getLTP("NSE:INFY-EQ")).thenReturn(100.0);
        String orderPayload = """
                {
                  "exchange": "NSE",
                  "symbol": "NSE:INFY-EQ",
                  "side": "BUY",
                  "qty": 10,
                  "orderType": "LIMIT",
                  "productType": "INTRADAY",
                  "price": 95,
                  "validity": "DAY"
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        Long orderId = paperOrderRepository.findByUserId(userId).getFirst().getId();

        String modifyPayload = """
                {
                  "qty": 20,
                  "price": 94
                }
                """;

        mockMvc.perform(put("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modifyPayload))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void approveSignal() throws Exception {
        StockScreeningResult signal = stockScreeningResultRepository.save(StockScreeningResult.builder()
                .userId(userId)
                .symbol("NSE:INFY-EQ")
                .signalScore(90)
                .grade("A")
                .entryPrice(MoneyUtils.bd(100))
                .scanTime(LocalDateTime.now())
                .approvalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                .build());

        mockMvc.perform(post("/api/strategy/signals/" + signal.getId() + "/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));
    }

    @Test
    void updateRiskLimits() throws Exception {
        String payload = """
                {
                  "dailyLossLimit": 1000,
                  "maxPositions": 5
                }
                """;
        mockMvc.perform(put("/api/risk/limits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPositions").value(5));
    }

    @Test
    void apiDocsAccessible() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthorizedWithoutToken() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forbiddenOnOtherUsersSignal() throws Exception {
        User other = userRepository.save(User.builder()
                .username("other")
                .passwordHash("pass")
                .role("USER")
                .availableFunds(MoneyUtils.bd(100000))
                .totalInvested(MoneyUtils.ZERO)
                .currentValue(MoneyUtils.bd(100000))
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build());
        StockScreeningResult signal = stockScreeningResultRepository.save(StockScreeningResult.builder()
                .userId(other.getId())
                .symbol("NSE:INFY-EQ")
                .signalScore(80)
                .grade("B")
                .entryPrice(MoneyUtils.bd(100))
                .scanTime(LocalDateTime.now())
                .approvalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                .build());

        mockMvc.perform(post("/api/strategy/signals/" + signal.getId() + "/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
