package com.apex.backend.controller;

import com.apex.backend.dto.ScanRequest;
import com.apex.backend.dto.ScanResponse;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.ManualScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scanner")
@RequiredArgsConstructor
@Tag(name = "Scanner")
public class ScannerController {

    private final ManualScanService manualScanService;

    @PostMapping("/run")
    @Operation(summary = "Run a manual scan")
    public ResponseEntity<ScanResponse> run(@AuthenticationPrincipal UserPrincipal principal,
                                            @Valid @RequestBody ScanRequest request) {
        Long userId = requireUserId(principal);
        return ResponseEntity.ok(manualScanService.runManualScan(userId, request));
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }
}
