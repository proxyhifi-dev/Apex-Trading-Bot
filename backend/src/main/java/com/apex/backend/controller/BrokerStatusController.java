package com.apex.backend.controller;

import com.apex.backend.dto.BrokerStatusResponse;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.BrokerConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/broker")
@RequiredArgsConstructor
@Tag(name = "Broker")
public class BrokerStatusController {

    private final BrokerConnectionService brokerConnectionService;

    @GetMapping("/status")
    @Operation(summary = "Get broker connection status")
    public ResponseEntity<BrokerStatusResponse> getStatus(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        return ResponseEntity.ok(brokerConnectionService.getStatus(userId));
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }
}
