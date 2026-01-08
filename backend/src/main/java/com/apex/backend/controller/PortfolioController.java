package com.apex.backend.controller;

import com.apex.backend.dto.ApiErrorResponse;
import com.apex.backend.model.PaperOrder;
import com.apex.backend.model.PaperPosition;
import com.apex.backend.model.PaperTrade;
import com.apex.backend.security.JwtTokenProvider;
import com.apex.backend.service.FyersAuthService;
import com.apex.backend.service.FyersService;
import com.apex.backend.service.PaperTradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/positions/open")
    public ResponseEntity<?> getOpenPositions(@RequestParam(defaultValue = "live") String mode,
                                              @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if ("paper".equalsIgnoreCase(mode)) {
                List<PaperPosition> positions = paperTradingService.getOpenPositions();
                return ResponseEntity.ok(positions);
            }
            String token = resolveFyersToken(authHeader);
            List<Map<String, Object>> openPositions = filterPositions(fyersService.getPositions(token), true);
            return ResponseEntity.ok(openPositions);
        } catch (Exception e) {
            log.error("Failed to fetch open positions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("Failed to fetch open positions", e.getMessage()));
        }
    }

    @GetMapping("/positions/closed")
    public ResponseEntity<?> getClosedPositions(@RequestParam(defaultValue = "live") String mode,
                                                @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if ("paper".equalsIgnoreCase(mode)) {
                List<PaperPosition> positions = paperTradingService.getClosedPositions();
                return ResponseEntity.ok(positions);
            }
            String token = resolveFyersToken(authHeader);
            List<Map<String, Object>> closedPositions = filterPositions(fyersService.getPositions(token), false);
            return ResponseEntity.ok(closedPositions);
        } catch (Exception e) {
            log.error("Failed to fetch closed positions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("Failed to fetch closed positions", e.getMessage()));
        }
    }

    @GetMapping("/orders")
    public ResponseEntity<?> getOrders(@RequestParam(defaultValue = "live") String mode,
                                       @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if ("paper".equalsIgnoreCase(mode)) {
                List<PaperOrder> orders = paperTradingService.getOrders();
                return ResponseEntity.ok(orders);
            }
            String token = resolveFyersToken(authHeader);
            return ResponseEntity.ok(fyersService.getOrders(token));
        } catch (Exception e) {
            log.error("Failed to fetch orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("Failed to fetch orders", e.getMessage()));
        }
    }

    @GetMapping("/trades")
    public ResponseEntity<?> getTrades(@RequestParam(defaultValue = "live") String mode,
                                       @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if ("paper".equalsIgnoreCase(mode)) {
                List<PaperTrade> trades = paperTradingService.getTrades();
                return ResponseEntity.ok(trades);
            }
            String token = resolveFyersToken(authHeader);
            return ResponseEntity.ok(fyersService.getTrades(token));
        } catch (Exception e) {
            log.error("Failed to fetch trades", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("Failed to fetch trades", e.getMessage()));
        }
    }

    private String resolveFyersToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalStateException("Missing Authorization header");
        }
        String jwt = authHeader.substring(7);
        Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
        String token = fyersAuthService.getFyersToken(userId);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Fyers account not linked");
        }
        return token;
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
