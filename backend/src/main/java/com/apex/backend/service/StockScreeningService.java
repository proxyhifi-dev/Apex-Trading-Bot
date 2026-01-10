package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.model.Candle;
import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.trading.pipeline.DecisionResult;
import com.apex.backend.trading.pipeline.PipelineRequest;
import com.apex.backend.trading.pipeline.TradeDecisionPipelineService;
import com.apex.backend.util.MoneyUtils;
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
    private final TradeDecisionPipelineService tradeDecisionPipelineService;
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
                if (m5.size() < 50) continue;

                DecisionResult decision = tradeDecisionPipelineService.evaluate(new PipelineRequest(
                        config.getTrading().getOwnerUserId(),
                        symbol,
                        "5",
                        m5,
                        null
                ));

                if (decision.action() == DecisionResult.DecisionAction.BUY) {
                    saveSignal(config.getTrading().getOwnerUserId(), decision);
                }
            } catch (Exception e) {
                log.error("Error screening {}: {}", symbol, e.getMessage());
            }
        }
    }

    public void saveSignal(Long userId, DecisionResult decision) {
        if (userId == null) {
            log.warn("⚠️ Skipping signal save because apex.trading.owner-user-id is not configured.");
            return;
        }
        StockScreeningResult result = StockScreeningResult.builder()
                .userId(userId)
                .symbol(decision.symbol())
                .signalScore((int) Math.round(decision.score()))
                .grade(decision.signalScore().grade())
                .entryPrice(MoneyUtils.bd(decision.signalScore().entryPrice()))
                .stopLoss(MoneyUtils.bd(decision.signalScore().suggestedStopLoss()))
                .scanTime(LocalDateTime.now())
                .approvalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                .analysisReason(decision.signalScore().reason())
                .build();
        screeningRepo.save(result);
        broadcastService.broadcastSignal(result);
    }
}
