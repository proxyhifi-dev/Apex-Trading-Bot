package com.apex.backend.controller;

import com.apex.backend.dto.UserProfileDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class AccountController {

    @Value("${apex.trading.capital:100000}")
    private double initialCapital;

    @GetMapping("/profile")
    public UserProfileDTO getProfile() {
        // TODO: Replace with real Fyers account data
        return UserProfileDTO.builder()
                .name("BOLLU ASHOK KUMAR")
                .availableFunds(initialCapital)
                .totalInvested(0.0)
                .currentValue(initialCapital)
                .todaysPnl(0.0)
                .holdings(new ArrayList<>())
                .build();
    }
}
