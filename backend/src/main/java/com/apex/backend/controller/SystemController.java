package com.apex.backend.controller;

import com.apex.backend.entity.SystemGuardState;
import com.apex.backend.model.OrderState;
import com.apex.backend.repository.OrderIntentRepository;
import com.apex.backend.repository.PaperOrderRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.service.EmergencyPanicService;
import com.apex.backend.service.SystemGuardService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemGuardService systemGuardService;
    private final EmergencyPanicService emergencyPanicService;
    private final TradeRepository tradeRepository;
    private final OrderIntentRepository orderIntentRepository;
    private final PaperOrderRepository paperOrderRepository;
    private final Environment environment;

    @Value("${apex.admin.token:}")
    private String adminToken;

    @Value("${guard.admin-token:}")
    private String legacyAdminToken;

    @PostMapping("/panic")
    public ResponseEntity<SystemStatusResponse> panic() {
        emergencyPanicService.triggerGlobalEmergency("MANUAL_PANIC");
        return ResponseEntity.ok(toStatus(systemGuardService.getState()));
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!isAuthorized(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Not authorized to reset panic"));
        }
        SystemGuardState state = systemGuardService.setPanicMode(false, "MANUAL_RESET", Instant.now());
        state = systemGuardService.setSafeMode(true, "PANIC_RESET", Instant.now());
        return ResponseEntity.ok(toStatus(state));
    }

    @GetMapping("/status")
    public ResponseEntity<SystemStatusResponse> status() {
        return ResponseEntity.ok(toStatus(systemGuardService.getState()));
    }

    private SystemStatusResponse toStatus(SystemGuardState state) {
        long openTrades = tradeRepository.countByStatus(com.apex.backend.model.Trade.TradeStatus.OPEN);
        long openOrders = orderIntentRepository.countByOrderStateIn(List.of(
                OrderState.CREATED, OrderState.SENT, OrderState.ACKED, OrderState.PART_FILLED, OrderState.CANCEL_REQUESTED))
                + paperOrderRepository.countByStatus("OPEN");
        return new SystemStatusResponse(
                state.getSystemMode(),
                state.isPanicMode(),
                state.getPanicReason(),
                state.getLastPanicReason(),
                state.getLastPanicAt(),
                state.getLastReconcileAt(),
                openTrades,
                openOrders
        );
    }

    private boolean isAuthorized(String token) {
        String expected = (adminToken != null && !adminToken.isBlank()) ? adminToken : legacyAdminToken;
        return isDevProfile() || (expected != null && !expected.isBlank() && expected.equals(token));
    }

    private boolean isDevProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(profile) || "local".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    public record SystemStatusResponse(SystemGuardState.SystemMode mode, boolean panicMode, String panicReason,
                                       String lastPanicReason, Instant lastPanicAt, Instant lastReconcileAt,
                                       long openPositions, long openOrders) {}
}
