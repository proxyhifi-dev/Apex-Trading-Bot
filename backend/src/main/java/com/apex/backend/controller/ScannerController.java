package com.apex.backend.controller;

import com.apex.backend.dto.ScannerRunRequest;
import com.apex.backend.dto.ScannerRunResponse;
import com.apex.backend.dto.ScannerRunResultResponse;
import com.apex.backend.dto.ScannerRunStatusResponse;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.ScannerRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/scanner")
@RequiredArgsConstructor
@Tag(name = "Scanner")
public class ScannerController {

    private final ScannerRunService scannerRunService;

    @PostMapping("/run")
    @Operation(summary = "Run an on-demand scan")
    @ApiResponse(responseCode = "200")
    public ResponseEntity<ScannerRunResponse> run(@AuthenticationPrincipal UserPrincipal principal,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                  @Valid @RequestBody ScannerRunRequest request) {
        Long userId = requireUserId(principal);
        return ResponseEntity.ok(scannerRunService.startRun(userId, idempotencyKey, request));
    }

    @GetMapping("/runs/{runId}")
    @Operation(summary = "Get scan run status")
    public ResponseEntity<ScannerRunStatusResponse> status(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable Long runId) {
        Long userId = requireUserId(principal);
        return ResponseEntity.ok(scannerRunService.getStatus(userId, runId));
    }

    @GetMapping("/runs/{runId}/results")
    @Operation(summary = "Get scan run results")
    public ResponseEntity<ScannerRunResultResponse> results(@AuthenticationPrincipal UserPrincipal principal,
                                                            @PathVariable Long runId) {
        Long userId = requireUserId(principal);
        return ResponseEntity.ok(scannerRunService.getResults(userId, runId));
    }

    @PostMapping("/runs/{runId}/cancel")
    @Operation(summary = "Cancel a scan run")
    public ResponseEntity<ScannerRunStatusResponse> cancel(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable Long runId) {
        Long userId = requireUserId(principal);
        return ResponseEntity.ok(scannerRunService.cancel(userId, runId));
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }
}
