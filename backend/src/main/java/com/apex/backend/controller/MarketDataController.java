package com.apex.backend.controller;

import com.apex.backend.model.Candle;
import com.apex.backend.exception.ConflictException;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.FyersAuthService;
import com.apex.backend.service.FyersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketDataController {

    private final FyersAuthService fyersAuthService;
    private final FyersService fyersService;

    @GetMapping("/ltp")
    public ResponseEntity<?> getLtp(@RequestParam String symbols,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        List<String> symbolList = List.of(symbols.split(","));
        String token = resolveFyersToken(requireUserId(principal));
        Map<String, BigDecimal> ltpMap = fyersService.getLtpBatch(symbolList, token);
        return ResponseEntity.ok(ltpMap);
    }

    @GetMapping("/candles")
    public ResponseEntity<?> getCandles(@RequestParam String symbol,
                                        @RequestParam(defaultValue = "5") String tf,
                                        @RequestParam(defaultValue = "200") int count,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        String token = resolveFyersToken(requireUserId(principal));
        List<Candle> candles = fyersService.getHistoricalData(symbol, count, tf, token);
        return ResponseEntity.ok(candles);
    }

    private String resolveFyersToken(Long userId) {
        String token = fyersAuthService.getFyersToken(userId);
        if (token == null || token.isBlank()) {
            throw new ConflictException("Fyers account not linked");
        }
        return token;
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }
}
