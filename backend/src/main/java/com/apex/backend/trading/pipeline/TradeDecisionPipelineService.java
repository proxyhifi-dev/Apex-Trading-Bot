package com.apex.backend.trading.pipeline;

import com.apex.backend.service.DataQualityGuard;
import com.apex.backend.service.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradeDecisionPipelineService {

    private final SignalEngine signalEngine;
    private final RiskEngine riskEngine;
    private final ExecutionEngine executionEngine;
    private final PortfolioEngine portfolioEngine;
    private final StrategyHealthEngine strategyHealthEngine;
    private final DataQualityGuard dataQualityGuard;
    private final MetricsService metricsService;

    public DecisionResult evaluate(PipelineRequest request) {
        List<String> reasons = new ArrayList<>();
        var dataQuality = dataQualityGuard.validate(request.timeframe(), request.candles());
        if (!dataQuality.allowed()) {
            reasons.addAll(dataQuality.reasons());
            return new DecisionResult(
                    request.symbol(),
                    DecisionResult.DecisionAction.HOLD,
                    0.0,
                    reasons,
                    new RiskDecision(false, 0.0, reasons, 1.0, 0),
                    null,
                    null,
                    null
            );
        }
        PortfolioSnapshot snapshot = request.portfolioSnapshot() != null
                ? request.portfolioSnapshot()
                : portfolioEngine.snapshot(request);

        SignalScore signalScore = signalEngine.score(request);
        metricsService.recordStrategySignal(signalScore.reason(), signalScore.score());
        StrategyHealthDecision healthDecision = strategyHealthEngine.evaluate(request.userId());
        if (healthDecision != null && healthDecision.status() == StrategyHealthDecision.StrategyHealthStatus.BROKEN) {
            reasons.addAll(healthDecision.reasons());
            return new DecisionResult(
                    request.symbol(),
                    DecisionResult.DecisionAction.HOLD,
                    signalScore.score(),
                    reasons,
                    new RiskDecision(false, 0.0, reasons, 1.0, 0),
                    null,
                    signalScore,
                    healthDecision
            );
        }
        RiskDecision riskDecision = riskEngine.evaluate(request, signalScore, snapshot);
        if (!riskDecision.allowed()) {
            reasons.addAll(riskDecision.reasons());
            return new DecisionResult(
                    request.symbol(),
                    DecisionResult.DecisionAction.HOLD,
                    signalScore.score(),
                    reasons,
                    riskDecision,
                    null,
                    signalScore,
                    healthDecision
            );
        }
        ExecutionPlan executionPlan = executionEngine.build(request, signalScore, riskDecision);
        DecisionResult.DecisionAction action = signalScore.tradable()
                ? DecisionResult.DecisionAction.BUY
                : DecisionResult.DecisionAction.HOLD;

        if (!signalScore.tradable()) {
            reasons.add(signalScore.reason());
        }
        return new DecisionResult(
                request.symbol(),
                action,
                signalScore.score(),
                reasons.isEmpty() ? List.of(signalScore.reason()) : reasons,
                riskDecision,
                executionPlan,
                signalScore,
                healthDecision
        );
    }
}
