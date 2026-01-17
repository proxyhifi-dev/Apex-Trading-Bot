package com.apex.backend.controller;

import com.apex.backend.dto.InstrumentDTO;
import com.apex.backend.exception.NotFoundException;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.model.InstrumentDefinition;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.InstrumentCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/instruments")
@RequiredArgsConstructor
@Tag(name = "Instruments")
public class InstrumentsController {

    private final InstrumentCacheService instrumentCacheService;
    private final Environment environment;

    @Value("${apex.admin.token:}")
    private String adminToken;

    @Value("${guard.admin-token:}")
    private String legacyAdminToken;

    @GetMapping("/search")
    @Operation(summary = "Search instruments")
    public ResponseEntity<List<InstrumentDTO>> search(@RequestParam String q,
                                                      @RequestParam(defaultValue = "20") int limit,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        requireUserId(principal);
        return ResponseEntity.ok(instrumentCacheService.search(q, limit));
    }

    @GetMapping("/{symbol}")
    @Operation(summary = "Get instrument by symbol")
    public ResponseEntity<InstrumentDTO> getInstrument(@PathVariable String symbol,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        requireUserId(principal);
        InstrumentDefinition instrument = instrumentCacheService.findBySymbol(symbol)
                .orElseThrow(() -> new NotFoundException("Instrument not found"));
        return ResponseEntity.ok(instrumentCacheService.toDto(instrument));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh instrument cache")
    public ResponseEntity<?> refresh(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal, token);
        instrumentCacheService.refresh();
        return ResponseEntity.ok().build();
    }

    private void requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
    }

    private void requireAdmin(UserPrincipal principal, String token) {
        if (principal == null || principal.getRole() == null || !"ADMIN".equalsIgnoreCase(principal.getRole())) {
            throw new org.springframework.security.access.AccessDeniedException("Admin role required");
        }
        if (isDevProfile()) {
            return;
        }
        String expected = adminToken != null && !adminToken.isBlank() ? adminToken : legacyAdminToken;
        if (expected == null || expected.isBlank() || !expected.equals(token)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid admin token");
        }
    }

    private boolean isDevProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(profile) || "local".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
