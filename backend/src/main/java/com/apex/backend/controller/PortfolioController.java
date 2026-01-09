package com.apex.backend.controller;

import com.apex.backend.model.PaperOrder;
import com.apex.backend.model.PaperPosition;
import com.apex.backend.model.PaperTrade;
import com.apex.backend.model.TradingMode;
import com.apex.backend.exception.ConflictException;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.FyersAuthService;
import com.apex.backend.service.FyersService;
import com.apex.backend.service.PaperTradingService;
import com.apex.backend.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PortfolioController {

    private final FyersAuthService fyersAuthService;
    private final FyersService fyersService;
    private final PaperTradingService paperTradingService;
    private final SettingsService settingsService;

    @GetMapping("/positions/open")
    public ResponseEntity<?> getOpenPositions(@AuthenticationPrincipal UserPrincipal principal) throws Exception {
        Long userId = requireUserId(principal);
        TradingMode mode = settingsService.getTradingMode(userId);
        if (mode == TradingMode.PAPER) {
            List<PaperPosition> positions = paperTradingService.getOpenPositions(userId);
            return ResponseEntity.ok(positions);
        }
        String token = resolveFyersToken(userId);
        List<Map<String, Object>> openPositions = filterPositions(fyersService.getPositions(token), true);
        return ResponseEntity.ok(openPositions);
    }

    @GetMapping("/positions/closed")
    public ResponseEntity<?> getClosedPositions(@AuthenticationPrincipal UserPrincipal principal) throws Exception {
        Long userId = requireUserId(principal);
        TradingMode mode = settingsService.getTradingMode(userId);
        if (mode == TradingMode.PAPER) {
            List<PaperPosition> positions = paperTradingService.getClosedPositions(userId);
            return ResponseEntity.ok(positions);
        }
        String token = resolveFyersToken(userId);
        List<Map<String, Object>> closedPositions = filterPositions(fyersService.getPositions(token), false);
        return ResponseEntity.ok(closedPositions);
    }

    @GetMapping("/orders")
    public ResponseEntity<?> getOrders(@AuthenticationPrincipal UserPrincipal principal) throws Exception {
        Long userId = requireUserId(principal);
        TradingMode mode = settingsService.getTradingMode(userId);
        if (mode == TradingMode.PAPER) {
            List<PaperOrder> orders = paperTradingService.getOrders(userId);
            return ResponseEntity.ok(orders);
        }
        String token = resolveFyersToken(userId);
        return ResponseEntity.ok(fyersService.getOrders(token));
    }

    @GetMapping("/trades")
    public ResponseEntity<?> getTrades(@AuthenticationPrincipal UserPrincipal principal) throws Exception {
        Long userId = requireUserId(principal);
        TradingMode mode = settingsService.getTradingMode(userId);
        if (mode == TradingMode.PAPER) {
            List<PaperTrade> trades = paperTradingService.getTrades(userId);
            return ResponseEntity.ok(trades);
        }
        String token = resolveFyersToken(userId);
        return ResponseEntity.ok(fyersService.getTrades(token));
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> filterPositions(Map<String, Object> response, boolean open) {
        Object data = response.get("data");
        if (!(data instanceof Map)) {
            return List.of();
        }
        Object netPositions = ((Map<String, Object>) data).get("netPositions");
        if (!(netPositions instanceof List)) {
            return List.of();
        }
        List<Map<String, Object>> positions = new ArrayList<>((List<Map<String, Object>>) netPositions);
        return positions.stream()
                .filter(position -> {
                    Object qtyObj = position.get("netQty");
                    double qty = qtyObj instanceof Number ? ((Number) qtyObj).doubleValue() : 0;
                    return open ? qty != 0 : qty == 0;
                })
                .toList();
    }
}
