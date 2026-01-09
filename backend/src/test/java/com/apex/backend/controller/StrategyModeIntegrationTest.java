package com.apex.backend.controller;

import com.apex.backend.model.User;
import com.apex.backend.repository.PaperAccountRepository;
import com.apex.backend.repository.SettingsRepository;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StrategyModeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SettingsRepository settingsRepository;

    @Autowired
    private PaperAccountRepository paperAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String token;

    @BeforeEach
    void setUp() {
        paperAccountRepository.deleteAll();
        settingsRepository.deleteAll();
        userRepository.deleteAll();

        User user = User.builder()
                .username("tester")
                .passwordHash(passwordEncoder.encode("password"))
                .email("tester@example.com")
                .role("USER")
                .availableFunds(BigDecimal.valueOf(100000.0))
                .totalInvested(BigDecimal.ZERO)
                .currentValue(BigDecimal.valueOf(100000.0))
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        user = userRepository.save(user);
        token = jwtTokenProvider.generateToken(user.getUsername(), user.getId(), user.getRole());
    }

    @Test
    void updatesAndReadsTradingMode() throws Exception {
        mockMvc.perform(post("/api/strategy/mode")
                        .param("mode", "LIVE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("LIVE"));

        mockMvc.perform(get("/api/strategy/mode")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("LIVE"));
    }

    @Test
    void resetsAndAdjustsPaperAccountBalance() throws Exception {
        mockMvc.perform(post("/api/paper/account/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startingCapital").value(100000.0))
                .andExpect(jsonPath("$.cashBalance").value(100000.0));

        mockMvc.perform(post("/api/paper/account/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").value(100500.0));

        mockMvc.perform(post("/api/paper/account/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":200}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").value(100300.0));
    }
}
