package com.apex.backend.controller;

import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.model.User;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.repository.WatchlistEntryRepository;
import com.apex.backend.security.JwtTokenProvider;
import com.apex.backend.util.MoneyUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SignalsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockScreeningResultRepository stockScreeningResultRepository;

    @Autowired
    private WatchlistEntryRepository watchlistEntryRepository;

    private String tokenUser1;
    private String tokenUser2;

    private Long user1Id;
    private Long user2Id;

    @BeforeEach
    void setup() {
        stockScreeningResultRepository.deleteAll();
        watchlistEntryRepository.deleteAll();
        userRepository.deleteAll();

        User user1 = userRepository.save(User.builder()
                .username("user1")
                .passwordHash("pass")
                .role("USER")
                .availableFunds(MoneyUtils.bd(100000))
                .totalInvested(MoneyUtils.ZERO)
                .currentValue(MoneyUtils.bd(100000))
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build());
        User user2 = userRepository.save(User.builder()
                .username("user2")
                .passwordHash("pass")
                .role("USER")
                .availableFunds(MoneyUtils.bd(100000))
                .totalInvested(MoneyUtils.ZERO)
                .currentValue(MoneyUtils.bd(100000))
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build());

        user1Id = user1.getId();
        user2Id = user2.getId();

        tokenUser1 = jwtTokenProvider.generateToken(user1.getUsername(), user1Id, user1.getRole());
        tokenUser2 = jwtTokenProvider.generateToken(user2.getUsername(), user2Id, user2.getRole());
    }

    @Test
    void recentSignalsAreUserScoped() throws Exception {
        stockScreeningResultRepository.save(StockScreeningResult.builder()
                .userId(user1Id)
                .symbol("NSE:INFY-EQ")
                .signalScore(90)
                .grade("A")
                .entryPrice(MoneyUtils.bd(100))
                .scanTime(LocalDateTime.now())
                .approvalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                .analysisReason("Reason")
                .build());
        stockScreeningResultRepository.save(StockScreeningResult.builder()
                .userId(user2Id)
                .symbol("NSE:TCS-EQ")
                .signalScore(80)
                .grade("B")
                .entryPrice(MoneyUtils.bd(200))
                .scanTime(LocalDateTime.now())
                .approvalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                .analysisReason("Reason")
                .build());

        mockMvc.perform(get("/api/signals/recent")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].symbol").value("NSE:INFY-EQ"));
    }

    @Test
    void scanNowReturnsWatchlistEmptyMessage() throws Exception {
        mockMvc.perform(post("/api/signals/scan-now")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Watchlist empty"));
    }
}
