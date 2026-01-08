package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.model.Candle;
import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.service.SmartSignalGenerator.SignalDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockScreeningService {

    private final StrategyConfig config;
    private final FyersService fyersService;
    private final SmartSignalGenerator signalGenerator;
    private final StockScreeningResultRepository screeningRepo;
    private final BroadcastService broadcastService;

    public List<String> getUniverse() {
        // Returns symbol list from application.properties
        return config.getTrading().getUniverse().getSymbols();
    }

    // ✅ FIXED: Updated to use Multi-Timeframe Data
    public void runScreening() {
        List<String> universe = getUniverse();
        for (String symbol : universe) {
            try {
                // Fetch Multi-Timeframe Data (to match SmartSignalGenerator signature)
                List<Candle> m5 = fyersService.getHistoricalData(symbol, 200, "5");
                List<Candle> m15 = fyersService.getHistoricalData(symbol, 200, "15");
                List<Candle> h1 = fyersService.getHistoricalData(symbol, 200, "60");
                List<Candle> daily = fyersService.getHistoricalData(symbol, 200, "D");

                if (m5.size() < 50) continue;

                // ✅ CALL NEW SIGNATURE
                SignalDecision decision = signalGenerator.generateSignalSmart(symbol, m5, m15, h1, daily);

                if (decision.isHasSignal()) {
                    saveSignal(decision);
                }
            } catch (Exception e) {
                log.error("Error screening {}: {}", symbol, e.getMessage());
            }
        }
    }

    private void saveSignal(SignalDecision decision) {
        StockScreeningResult result = StockScreeningResult.builder()
                .symbol(decision.getSymbol())
                .signalScore(decision.getScore())
                .grade(decision.getGrade())
                .entryPrice(decision.getEntryPrice())
                .stopLoss(decision.getSuggestedStopLoss())
                .scanTime(LocalDateTime.now())
                .approvalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                .analysisReason(decision.getReason())
                .build();
        screeningRepo.save(result);
        broadcastService.broadcastSignal(result);
    }
}
