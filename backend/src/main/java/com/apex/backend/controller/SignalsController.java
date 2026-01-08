package com.apex.backend.controller;

import com.apex.backend.dto.ApiErrorResponse;
import com.apex.backend.dto.SignalDTO;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.service.BotScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/signals")
@RequiredArgsConstructor
public class SignalsController {

    private final BotScheduler botScheduler;
    private final StockScreeningResultRepository screeningRepository;

    @PostMapping("/scan-now")
    public ResponseEntity<?> scanNow() {
        try {
            new Thread(botScheduler::forceScan).start();
            return ResponseEntity.ok(new MessageResponse("Scan triggered"));
        } catch (Exception e) {
            log.error("Failed to trigger scan", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("Failed to trigger scan", e.getMessage()));
        }
    }

    @GetMapping("/recent")
    public ResponseEntity<?> recentSignals(@RequestParam(defaultValue = "live") String mode) {
        try {
            List<SignalDTO> signals = screeningRepository.findTop50ByOrderByScanTimeDesc()
                    .stream()
                    .map(result -> SignalDTO.builder()
                            .id(result.getId())
                            .symbol(result.getSymbol())
                            .signalScore(result.getSignalScore())
                            .grade(result.getGrade())
                            .entryPrice(result.getEntryPrice() > 0 ? result.getEntryPrice() : result.getCurrentPrice())
                            .scanTime(result.getScanTime())
                            .hasEntrySignal(true)
                            .build())
                    .collect(Collectors.toList());
            return ResponseEntity.ok(signals);
        } catch (Exception e) {
            log.error("Failed to fetch recent signals", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("Failed to fetch recent signals", e.getMessage()));
        }
    }

    public static class MessageResponse {
        public String message;
        public long timestamp;

        public MessageResponse(String message) {
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
