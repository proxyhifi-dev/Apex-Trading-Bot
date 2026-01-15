package com.apex.backend.controller;

import com.apex.backend.entity.SystemGuardState;
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
import java.util.Map;

@RestController
@RequestMapping("/api/guard")
@RequiredArgsConstructor
public class GuardController {

    private final SystemGuardService systemGuardService;
    private final Environment environment;

    @Value("${guard.admin-token:}")
    private String adminToken;

    @GetMapping("/state")
    public ResponseEntity<?> getState() {
        SystemGuardState state = systemGuardService.getState();
        return ResponseEntity.ok(new GuardStateResponse(
                state.isSafeMode(),
                state.getLastReconcileAt(),
                state.getLastMismatchAt(),
                state.getLastMismatchReason()
        ));
    }

    @PostMapping("/clear")
    public ResponseEntity<?> clearSafeMode(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!isDevProfile() && (adminToken == null || adminToken.isBlank() || !adminToken.equals(token))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Not authorized to clear safe mode"));
        }
        SystemGuardState state = systemGuardService.clearSafeMode();
        return ResponseEntity.ok(new GuardStateResponse(
                state.isSafeMode(),
                state.getLastReconcileAt(),
                state.getLastMismatchAt(),
                state.getLastMismatchReason()
        ));
    }

    private boolean isDevProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(profile) || "local".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    public record GuardStateResponse(boolean safeMode, Instant lastReconcileAt, Instant lastMismatchAt, String lastMismatchReason) {}
}
