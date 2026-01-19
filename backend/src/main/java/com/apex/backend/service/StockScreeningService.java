package com.apex.backend.service;

import com.apex.backend.model.StockScreeningResult;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.trading.pipeline.DecisionResult;
import com.apex.backend.util.MoneyUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockScreeningService {

    private final StockScreeningResultRepository screeningRepo;
    private final BroadcastService broadcastService;
    private final WatchlistService watchlistService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<String> getUniverse(Long userId) {
        return watchlistService.resolveSymbolsForUser(userId);
    }

    public void saveSignal(Long userId, DecisionResult decision) {
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
                .scoreBreakdown(serializeScoreBreakdown(decision))
                .build();
        screeningRepo.save(result);
        broadcastService.broadcastSignal(result);
    }

    private String serializeScoreBreakdown(DecisionResult decision) {
        if (decision == null || decision.signalScore() == null || decision.signalScore().featureContributions() == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(decision.signalScore().featureContributions());
        } catch (Exception e) {
            log.warn("Failed to serialize score breakdown for {}", decision.symbol(), e);
            return null;
        }
    }
}
