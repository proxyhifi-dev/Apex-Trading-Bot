package com.apex.backend.service;

import com.apex.backend.trading.pipeline.DecisionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaperOrderService {

    public void placeFromSignal(Long userId, DecisionResult decision) {
        if (decision == null) {
            return;
        }
        String reason = decision.signalScore() != null ? decision.signalScore().reason() : "n/a";
        log.info("Paper order intent (stub): userId={} symbol={} score={} reason={}",
                userId,
                decision.symbol(),
                decision.score(),
                reason);
    }
}
