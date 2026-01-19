package com.apex.backend.controller;

import com.apex.backend.dto.WatchlistEntryRequest;
import com.apex.backend.model.User;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.repository.WatchlistEntryRepository;
import com.apex.backend.security.JwtTokenProvider;
import com.apex.backend.util.MoneyUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WatchlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WatchlistEntryRepository watchlistEntryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String tokenUser1;
    private String tokenUser2;

    @BeforeEach
    void setup() {
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

        tokenUser1 = jwtTokenProvider.generateToken(user1.getUsername(), user1.getId(), user1.getRole());
        tokenUser2 = jwtTokenProvider.generateToken(user2.getUsername(), user2.getId(), user2.getRole());
    }

    @Test
    void watchlistIsUserScoped() throws Exception {
        WatchlistEntryRequest request = new WatchlistEntryRequest();
        request.setSymbol("INFY-EQ");
        request.setExchange("NSE");

        mockMvc.perform(post("/api/watchlist")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("INFY-EQ"));

        mockMvc.perform(get("/api/watchlist")
                        .header("Authorization", "Bearer " + tokenUser2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/watchlist")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exchange").value("NSE"));
    }

    @Test
    void deleteWatchlistEntryRequiresOwnership() throws Exception {
        WatchlistEntryRequest request = new WatchlistEntryRequest();
        request.setSymbol("RELIANCE-EQ");
        request.setExchange("NSE");

        String response = mockMvc.perform(post("/api/watchlist")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long entryId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(delete("/api/watchlist/" + entryId)
                        .header("Authorization", "Bearer " + tokenUser2))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/watchlist/" + entryId)
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isNoContent());
    }
}
