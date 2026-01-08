package com.apex.backend.controller;

import com.apex.backend.dto.BotStatusResponse;
import com.apex.backend.service.BotStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
public class BotStatusController {

    private final BotStatusService botStatusService;

    @GetMapping("/status")
    public ResponseEntity<BotStatusResponse> getStatus() {
        log.info("Fetching bot status");
        return ResponseEntity.ok(botStatusService.getStatus());
    }
}
