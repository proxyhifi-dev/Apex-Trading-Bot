package com.apex.backend.controller;

import com.apex.backend.model.Candle;
import com.apex.backend.exception.ConflictException;
import com.apex.backend.exception.FyersApiException;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.FyersService;
import com.apex.backend.service.FyersTokenService;
import com.apex.backend.service.BrokerStatusService;
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

    private final FyersService fyersService;
    private final FyersTokenService fyersTokenService;
    private final BrokerStatusService brokerStatusService;

    @GetMapping("/ltp")
    public ResponseEntity<?> getLtp(@RequestParam String symbols,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        List<String> symbolList = List.of(symbols.split(","));
        Long userId = requireUserId(principal);
        String token = resolveFyersToken(userId);
        Map<String, BigDecimal> ltpMap = invokeMarketData(userId, () -> fyersService.getLtpBatch(symbolList, token));
        return ResponseEntity.ok(ltpMap);
    }

    @GetMapping("/candles")
    public ResponseEntity<?> getCandles(@RequestParam String symbol,
                                        @RequestParam(defaultValue = "5") String tf,
                                        @RequestParam(defaultValue = "200") int count,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        String token = resolveFyersToken(userId);
        List<Candle> candles = invokeMarketData(userId, () -> fyersService.getHistoricalData(symbol, count, tf, token));
        return ResponseEntity.ok(candles);
    }

    private String resolveFyersToken(Long userId) {
        String token = fyersTokenService.getAccessToken(userId);
        if (token == null || token.isBlank()) {
            brokerStatusService.markDegraded("FYERS", "TOKEN_MISSING");
            throw new ConflictException("FYERS token missing or expired");
        }
        return token;
    }

    private <T> T invokeMarketData(Long userId, java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (FyersApiException ex) {
            if (ex.getStatusCode() == 401 || ex.getStatusCode() == 403) {
                brokerStatusService.markDegraded("FYERS", "TOKEN_EXPIRED");
                throw new ConflictException("FYERS token missing or expired");
            }
            throw ex;
        }
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }
}
