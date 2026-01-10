package com.apex.backend.controller;

import com.apex.backend.dto.TradeAttributionResponse;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.repository.TradeFeatureAttributionRepository;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.PortfolioRiskAnalyticsService;
import com.apex.backend.service.PortfolioRiskAnalyticsService.PortfolioRiskSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final TradeFeatureAttributionRepository tradeFeatureAttributionRepository;
    private final PortfolioRiskAnalyticsService portfolioRiskAnalyticsService;

    @GetMapping("/trades/{id}/attribution")
    public List<TradeAttributionResponse> attribution(@PathVariable Long id,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        return tradeFeatureAttributionRepository.findByTradeIdAndUserId(id, userId)
                .stream()
                .map(row -> new TradeAttributionResponse(
                        row.getFeature(),
                        row.getNormalizedValue(),
                        row.getWeight(),
                        row.getContribution()
                ))
                .toList();
    }

    @GetMapping("/portfolio/risk")
    public PortfolioRiskSnapshot portfolioRisk(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        return portfolioRiskAnalyticsService.getRiskSnapshot(userId);
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }
}
