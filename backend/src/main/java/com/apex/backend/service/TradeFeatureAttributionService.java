package com.apex.backend.service;

import com.apex.backend.model.TradeFeatureAttribution;
import com.apex.backend.repository.TradeFeatureAttributionRepository;
import com.apex.backend.trading.pipeline.FeatureContribution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradeFeatureAttributionService {

    private final TradeFeatureAttributionRepository repository;

    public void saveAttributions(Long tradeId, Long userId, String symbol, List<FeatureContribution> contributions) {
        if (tradeId == null || userId == null || contributions == null) {
            return;
        }
        List<TradeFeatureAttribution> entities = contributions.stream()
                .map(contribution -> TradeFeatureAttribution.builder()
                        .tradeId(tradeId)
                        .userId(userId)
                        .symbol(symbol)
                        .feature(contribution.feature())
                        .normalizedValue(contribution.normalizedValue())
                        .weight(contribution.weight())
                        .contribution(contribution.contribution())
                        .createdAt(LocalDateTime.now())
                        .build())
                .toList();
        repository.saveAll(entities);
    }
}
