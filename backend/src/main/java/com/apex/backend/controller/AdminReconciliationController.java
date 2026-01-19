package com.apex.backend.controller;

import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.risk.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/api/admin/reconcile")
@RequiredArgsConstructor
@Tag(name = "Admin Reconcile")
public class AdminReconciliationController {

    private final ReconciliationService reconciliationService;

    @PostMapping("/run")
    @Operation(summary = "Run reconciliation")
    public ResponseEntity<ReconciliationService.ReconcileReport> run(@AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);
        return ResponseEntity.ok(reconciliationService.reconcile());
    }

    @GetMapping("/last-report")
    @Operation(summary = "Get last reconciliation report")
    public ResponseEntity<ReconciliationService.ReconcileReport> lastReport(@AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);
        return ResponseEntity.ok(reconciliationService.getLastReport());
    }

    @PostMapping("/repair")
    @Operation(summary = "Attempt reconciliation repair")
    public ResponseEntity<ReconciliationService.ReconcileRepairReport> repair(@AuthenticationPrincipal UserPrincipal principal,
                                                                              @RequestBody(required = false) ReconciliationService.ReconcileRepairRequest request) {
        requireAdmin(principal);
        ReconciliationService.ReconcileRepairRequest effective = request != null
                ? request
                : new ReconciliationService.ReconcileRepairRequest(null, true, true, false);
        log.warn("Admin reconcile repair requested: {}", effective);
        return ResponseEntity.ok(reconciliationService.repair(effective));
    }

    private void requireAdmin(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        if (principal.getRole() == null || !principal.getRole().equalsIgnoreCase("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
    }
}
