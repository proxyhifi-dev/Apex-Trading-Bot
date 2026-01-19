package com.apex.backend.controller;

import com.apex.backend.dto.AccountOverviewDTO;
import com.apex.backend.dto.DashboardResponse;
import com.apex.backend.dto.SignalDTO;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.model.PaperAccount;
import com.apex.backend.model.TradingMode;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.AccountOverviewService;
import com.apex.backend.service.BrokerConnectionService;
import com.apex.backend.service.FyersService;
import com.apex.backend.service.PaperTradingService;
import com.apex.backend.service.SettingsService;
import com.apex.backend.repository.StockScreeningResultRepository;
import com.apex.backend.util.MoneyUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard")
public class DashboardController {

    private final BrokerConnectionService brokerConnectionService;
    private final AccountOverviewService accountOverviewService;
    private final StockScreeningResultRepository stockScreeningResultRepository;
    private final PaperTradingService paperTradingService;
    private final SettingsService settingsService;
    private final FyersService fyersService;

    @GetMapping
    @Operation(summary = "Get aggregated dashboard data")
    public ResponseEntity<DashboardResponse> getDashboard(@AuthenticationPrincipal UserPrincipal principal) throws Exception {
        Long userId = requireUserId(principal);
        AccountOverviewDTO overview = accountOverviewService.buildOverview(userId);
        List<SignalDTO> latestSignals = stockScreeningResultRepository.findTop50ByUserIdOrderByScanTimeDesc(userId)
                .stream()
                .limit(10)
                .map(result -> SignalDTO.builder()
                        .id(result.getId())
                        .symbol(result.getSymbol())
                        .signalScore(result.getSignalScore())
                        .grade(result.getGrade())
                        .entryPrice(result.getEntryPrice())
                        .scanTime(result.getScanTime())
                        .hasEntrySignal(true)
                        .build())
                .toList();

        List<AccountOverviewDTO.PositionDTO> openPositions = overview.getPositions() != null
                ? overview.getPositions()
                : List.of();

        DashboardResponse.RiskStatusSummary riskStatus = buildRiskStatus(userId, overview);
        DashboardResponse.TodayPnlSummary pnlSummary = buildTodayPnl(userId, overview);

        return ResponseEntity.ok(DashboardResponse.builder()
                .brokerStatus(brokerConnectionService.getStatus(userId))
                .accountOverview(overview)
                .latestSignals(latestSignals)
                .openPositions(openPositions)
                .riskStatus(riskStatus)
                .todayPnl(pnlSummary)
                .generatedAt(LocalDateTime.now())
                .build());
    }

    private DashboardResponse.RiskStatusSummary buildRiskStatus(Long userId, AccountOverviewDTO overview) throws Exception {
        TradingMode mode = settingsService.getTradingMode(userId);
        BigDecimal equity = MoneyUtils.ZERO;
        int openPositions = overview.getPositions() != null ? overview.getPositions().size() : 0;
        if (mode == TradingMode.PAPER) {
            PaperAccount account = paperTradingService.getAccount(userId);
            BigDecimal positionValue = paperTradingService.getOpenPositionsMarketValue(userId);
            equity = MoneyUtils.add(account.getCashBalance(), positionValue);
        } else {
            Map<String, Object> funds = fyersService.getFundsForUser(userId);
            equity = MoneyUtils.add(extractFundValue(funds, "cash"), extractFundValue(funds, "used"));
        }
        return DashboardResponse.RiskStatusSummary.builder()
                .equity(equity)
                .openPositions(openPositions)
                .build();
    }

    private DashboardResponse.TodayPnlSummary buildTodayPnl(Long userId, AccountOverviewDTO overview) throws Exception {
        TradingMode mode = settingsService.getTradingMode(userId);
        if (mode == TradingMode.PAPER) {
            PaperAccount account = paperTradingService.getAccount(userId);
            BigDecimal unrealized = paperTradingService.getOpenPositionsUnrealizedPnl(userId);
            BigDecimal realized = account.getRealizedPnl();
            BigDecimal total = MoneyUtils.add(realized, unrealized);
            return DashboardResponse.TodayPnlSummary.builder()
                    .realized(realized)
                    .unrealized(unrealized)
                    .total(total)
                    .build();
        }
        BigDecimal dayPnl = overview.getFunds() != null ? overview.getFunds().getDayPnl() : MoneyUtils.ZERO;
        return DashboardResponse.TodayPnlSummary.builder()
                .realized(dayPnl)
                .unrealized(MoneyUtils.ZERO)
                .total(dayPnl)
                .build();
    }

    private BigDecimal extractFundValue(Map<String, Object> funds, String key) {
        Object data = funds.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object value = dataMap.get(key);
            if (value instanceof Number number) {
                return MoneyUtils.bd(number.doubleValue());
            }
        }
        return MoneyUtils.ZERO;
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }
}
