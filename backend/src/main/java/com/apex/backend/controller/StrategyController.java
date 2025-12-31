package com.apex.backend.controller;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.dto.SignalDTO;
import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.service.BotScheduler;
import com.apex.backend.service.TradeExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/strategy") // ✅ Fixed: Changed from /api/bot
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:4200")
public class StrategyController {

    private final StockScreeningResultRepository screeningRepo;
    private final TradeExecutionService tradeExecutionService;
    private final BotScheduler botScheduler;
    private final StrategyConfig config;

    @PostMapping("/scan-now")
    public ResponseEntity<String> manualScan() {
        new Thread(botScheduler::forceScan).start();
        return ResponseEntity.ok("Market Scan Triggered!");
    }

    // ✅ Match frontend call: /api/strategy/signals
    @GetMapping("/signals")
    public List<SignalDTO> getAllSignals() {
        return screeningRepo.findAll()
                .stream()
                .map(s -> SignalDTO.builder()
                        .id(s.getId())
                        .symbol(s.getSymbol())
                        .signalScore(s.getSignalScore())
                        .grade(s.getGrade())
                        .entryPrice(s.getEntryPrice() > 0 ? s.getEntryPrice() : s.getCurrentPrice())
                        .scanTime(s.getScanTime())
                        .hasEntrySignal(true)
                        .build())
                .collect(Collectors.toList());
    }

    @GetMapping("/signals/pending")
    public List<SignalDTO> getPendingSignals() {
        return screeningRepo.findByApprovalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                .stream()
                .map(s -> SignalDTO.builder()
                        .id(s.getId())
                        .symbol(s.getSymbol())
                        .entryPrice(s.getEntryPrice())
                        .build())
                .collect(Collectors.toList());
    }

    @PostMapping("/mode")
    public ResponseEntity<?> toggleMode(@RequestParam boolean paperMode) {
        config.getTrading().setPaperMode(paperMode);
        return ResponseEntity.ok(Map.of("paperMode", paperMode));
    }

    @GetMapping("/mode")
    public ResponseEntity<?> getMode() {
        return ResponseEntity.ok(Map.of("paperMode", config.getTrading().isPaperMode()));
    }
}