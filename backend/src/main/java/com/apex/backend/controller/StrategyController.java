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
@RequestMapping("/api/bot")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:4200")
public class StrategyController {

    private final StockScreeningResultRepository screeningRepo;
    private final TradeExecutionService tradeExecutionService;
    private final BotScheduler botScheduler;
    private final StrategyConfig config; // ✅ Added Config Injection

    // --- MANUAL CONTROLS ---

    @PostMapping("/scan-now")
    public ResponseEntity<String> manualScan() {
        new Thread(botScheduler::forceScan).start();
        return ResponseEntity.ok("Market Scan Triggered!");
    }

    // ✅ NEW: Toggle Paper/Live Mode (MISSING IN YOUR CODE)
    @PostMapping("/mode")
    public ResponseEntity<?> toggleMode(@RequestParam boolean paperMode) {
        config.getTrading().setPaperMode(paperMode);
        log.info("🔄 System Mode Changed to: {}", paperMode ? "PAPER TRADING" : "LIVE TRADING");
        return ResponseEntity.ok(Map.of("paperMode", paperMode));
    }

    // ✅ NEW: Get Current Mode (MISSING IN YOUR CODE)
    @GetMapping("/mode")
    public ResponseEntity<?> getMode() {
        return ResponseEntity.ok(Map.of("paperMode", config.getTrading().isPaperMode()));
    }

    // --- SIGNAL MANAGEMENT ---

    @GetMapping("/signals/pending")
    public List<SignalDTO> getPendingSignals() {
        return screeningRepo.findByApprovalStatus(StockScreeningResult.ApprovalStatus.PENDING)
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

    @PostMapping("/signals/{id}/approve")
    public void approveSignal(@PathVariable Long id, @RequestParam boolean isPaper) {
        // You can allow the frontend to override, or force the global config mode here
        // For now, we respect the frontend parameter, but you might want to use:
        // boolean effectiveMode = config.getTrading().isPaperMode();
        tradeExecutionService.approveAndExecute(id, isPaper);
    }

    @PostMapping("/signals/{id}/reject")
    public void rejectSignal(@PathVariable Long id) {
        StockScreeningResult signal = screeningRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Signal not found"));
        signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.REJECTED);
        screeningRepo.save(signal);
    }
}