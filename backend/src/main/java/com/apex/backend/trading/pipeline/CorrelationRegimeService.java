package com.apex.backend.trading.pipeline;

import com.apex.backend.config.RiskProperties;
import com.apex.backend.model.CorrelationMatrixDetailed;
import com.apex.backend.model.CorrelationRegimeState;
import com.apex.backend.model.Trade;
import com.apex.backend.repository.CorrelationMatrixDetailedRepository;
import com.apex.backend.repository.CorrelationRegimeStateRepository;
import com.apex.backend.repository.TradeRepository;
import com.apex.backend.service.CorrelationService;
import com.apex.backend.service.FyersService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CorrelationRegimeService {

    private final RiskProperties riskProperties;
    private final TradeRepository tradeRepository;
    private final FyersService fyersService;
    private final CorrelationService correlationService;
    private final CorrelationRegimeStateRepository regimeStateRepository;
    private final CorrelationMatrixDetailedRepository detailedRepository;

    public CorrelationRegimeState updateRegime(Long userId) {
        if (userId == null) {
            return null;
        }
        List<Trade> openTrades = tradeRepository.findByUserIdAndStatus(userId, Trade.TradeStatus.OPEN);
        if (openTrades.size() < 2) {
            return storeState(userId, 0.0);
        }
        Map<String, List<Double>> series = new HashMap<>();
        int lookback = riskProperties.getCorrelation().getLookback();
        for (Trade trade : openTrades) {
            var candles = fyersService.getHistoricalData(trade.getSymbol(), lookback, "D");
            series.put(trade.getSymbol(), candles.stream().map(c -> c.getClose()).toList());
        }
        CorrelationService.CorrelationMatrix matrix = correlationService.buildCorrelationMatrix(series);
        double avgOffDiagonal = computeAverageOffDiagonal(matrix.matrix);
        persistDetailed(userId, matrix);
        return storeState(userId, avgOffDiagonal);
    }

    public double getSizingMultiplier(Long userId) {
        CorrelationRegimeState state = regimeStateRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        if (state == null) {
            return 1.0;
        }
        return state.getSizingMultiplier() == null ? 1.0 : state.getSizingMultiplier();
    }

    private double computeAverageOffDiagonal(double[][] matrix) {
        if (matrix == null || matrix.length < 2) {
            return 0.0;
        }
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix.length; j++) {
                if (i == j) {
                    continue;
                }
                sum += matrix[i][j];
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private void persistDetailed(Long userId, CorrelationService.CorrelationMatrix matrix) {
        if (matrix == null || matrix.stocks == null || matrix.matrix == null) {
            return;
        }
        List<CorrelationMatrixDetailed> rows = new ArrayList<>();
        for (int i = 0; i < matrix.stocks.size(); i++) {
            for (int j = i + 1; j < matrix.stocks.size(); j++) {
                rows.add(CorrelationMatrixDetailed.builder()
                        .userId(userId)
                        .symbolA(matrix.stocks.get(i))
                        .symbolB(matrix.stocks.get(j))
                        .correlation(matrix.matrix[i][j])
                        .calculatedAt(LocalDateTime.now())
                        .build());
            }
        }
        detailedRepository.saveAll(rows);
    }

    private CorrelationRegimeState storeState(Long userId, double avgOffDiagonal) {
        boolean spike = avgOffDiagonal >= riskProperties.getCorrelation().getSpikeThreshold();
        double sizingMultiplier = spike ? riskProperties.getCorrelation().getSizingMultiplierOnSpike() : 1.0;
        CorrelationRegimeState state = CorrelationRegimeState.builder()
                .userId(userId)
                .regime(spike ? "SPIKE" : "NORMAL")
                .avgOffDiagonalCorrelation(avgOffDiagonal)
                .sizingMultiplier(sizingMultiplier)
                .createdAt(LocalDateTime.now())
                .build();
        return regimeStateRepository.save(state);
    }
}
