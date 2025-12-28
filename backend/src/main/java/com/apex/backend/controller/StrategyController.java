package com.apex.backend.controller;

import com.apex.backend.dto.SignalDTO;
import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.service.TradeExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/strategies")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:4200")
public class StrategyController {

    private final StockScreeningResultRepository screeningRepo;
    private final TradeExecutionService tradeExecutionService;

    @GetMapping("/signals/pending")
    public List<SignalDTO> getPendingSignals() {
        List<StockScreeningResult> pending = screeningRepo
                .findByApprovalStatus(StockScreeningResult.ApprovalStatus.PENDING);

        return pending.stream()
                .map(s -> SignalDTO.builder()
                        .id(s.getId())
                        .symbol(s.getSymbol())
                        .signalScore(s.getSignalScore())
                        .entryPrice(s.getCurrentPrice())
                        .scanTime(s.getScanTime())
                        .hasEntrySignal(s.getHasEntrySignal())  // FIXED: Use getter
                        .build())
                .collect(Collectors.toList());
    }

    @PostMapping("/signals/{id}/approve")
    public void approveSignal(@PathVariable Long id, @RequestParam boolean isPaper) {
        log.info("Approving signal {} (isPaper={})", id, isPaper);
        tradeExecutionService.approveAndExecute(id, isPaper);  // FIXED: Use correct method name
    }

    @PostMapping("/signals/{id}/reject")
    public void rejectSignal(@PathVariable Long id) {
        log.info("Rejecting signal {}", id);
        StockScreeningResult signal = screeningRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Signal not found"));

        signal.setApprovalStatus(StockScreeningResult.ApprovalStatus.REJECTED);
        screeningRepo.save(signal);
    }
}
