package com.apex.backend.controller;

import com.apex.backend.dto.BotHealthResponse;
import com.apex.backend.dto.BotStateResponse;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.model.BotState;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.BotOpsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
@Tag(name = "Bot")
public class BotOpsController {

    private final BotOpsService botOpsService;

    @PostMapping("/start")
    @Operation(summary = "Start bot")
    public ResponseEntity<BotStateResponse> start(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        log.info("Starting bot for user {}", userId);
        return ResponseEntity.ok(toResponse(botOpsService.start(userId)));
    }

    @PostMapping("/stop")
    @Operation(summary = "Stop bot")
    public ResponseEntity<BotStateResponse> stop(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        log.info("Stopping bot for user {}", userId);
        return ResponseEntity.ok(toResponse(botOpsService.stop(userId)));
    }

    @GetMapping("/health")
    @Operation(summary = "Bot health")
    public ResponseEntity<BotHealthResponse> health(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        BotState state = botOpsService.getState(userId);
        BotHealthResponse response = BotHealthResponse.builder()
                .running(state.isRunning())
                .lastScanAt(state.getLastScanAt())
                .nextScanAt(state.getNextScanAt())
                .lastError(state.getLastError())
                .lastErrorAt(state.getLastErrorAt())
                .threadAlive(Boolean.TRUE.equals(state.getThreadAlive()))
                .queueDepth(state.getQueueDepth())
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reload-config")
    @Operation(summary = "Reload bot config")
    public ResponseEntity<BotStateResponse> reload(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        return ResponseEntity.ok(toResponse(botOpsService.reloadConfig(userId)));
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }

    private BotStateResponse toResponse(BotState state) {
        return BotStateResponse.builder()
                .running(state.isRunning())
                .lastError(state.getLastError())
                .lastErrorAt(state.getLastErrorAt())
                .build();
    }
}
