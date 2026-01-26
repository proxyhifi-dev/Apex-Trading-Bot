package com.apex.backend.controller;

import com.apex.backend.dto.UiConfigDTO;
import com.apex.backend.dto.UiEndpointDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ui")
public class UiConfigController {

    private final String apiBaseUrl;
    private final String wsBaseUrl;

    public UiConfigController(
        @Value("${apex.ui.api-base-url:http://127.0.0.1:8080/api}") String apiBaseUrl,
        @Value("${apex.ui.ws-url:ws://127.0.0.1:8080/ws}") String wsBaseUrl
    ) {
        this.apiBaseUrl = apiBaseUrl;
        this.wsBaseUrl = wsBaseUrl;
    }

    @GetMapping("/config")
    public ResponseEntity<UiConfigDTO> getUiConfig() {
        List<UiEndpointDTO> endpoints = List.of(
            UiEndpointDTO.builder().method("GET").path("/api/auth/fyers/auth-url").description("Get broker OAuth URL").build(),
            UiEndpointDTO.builder().method("POST").path("/api/auth/fyers/callback").description("Exchange auth code and return tokens/profile").build(),
            UiEndpointDTO.builder().method("POST").path("/api/auth/fyers/link-temp-token").description("Link temp broker token to logged-in user").build(),
            UiEndpointDTO.builder().method("POST").path("/api/auth/register").description("Register user").build(),
            UiEndpointDTO.builder().method("POST").path("/api/auth/login").description("Login user").build(),
            UiEndpointDTO.builder().method("POST").path("/api/auth/refresh").description("Refresh access token").build(),
            UiEndpointDTO.builder().method("GET").path("/api/auth/fyers/status").description("Fyers connection status").build(),
            UiEndpointDTO.builder().method("GET").path("/api/account/profile").description("Profile snapshot").build(),
            UiEndpointDTO.builder().method("GET").path("/api/account/summary?type=PAPER|LIVE").description("Summary snapshot").build(),
            UiEndpointDTO.builder().method("GET").path("/api/account/capital").description("Capital info").build(),
            UiEndpointDTO.builder().method("GET").path("/api/account/overview").description("Full account overview").build(),
            UiEndpointDTO.builder().method("GET").path("/api/settings").description("UI settings").build(),
            UiEndpointDTO.builder().method("GET").path("/api/trades/history").description("All trades").build(),
            UiEndpointDTO.builder().method("GET").path("/api/trades/performance").description("Performance metrics").build(),
            UiEndpointDTO.builder().method("GET").path("/api/trades/by-symbol?symbol=...").description("Trades by symbol").build(),
            UiEndpointDTO.builder().method("GET").path("/api/trades/open").description("Open positions").build(),
            UiEndpointDTO.builder().method("GET").path("/api/trades/closed").description("Closed positions").build(),
            UiEndpointDTO.builder().method("GET").path("/api/paper/positions/open").description("Open paper positions").build(),
            UiEndpointDTO.builder().method("GET").path("/api/paper/positions/closed").description("Closed paper positions").build(),
            UiEndpointDTO.builder().method("GET").path("/api/paper/positions").description("Paper positions").build(),
            UiEndpointDTO.builder().method("GET").path("/api/paper/summary").description("Paper summary").build(),
            UiEndpointDTO.builder().method("GET").path("/api/paper/stats").description("Paper stats").build(),
            UiEndpointDTO.builder().method("POST").path("/api/scanner/run").description("Manual scan").build(),
            UiEndpointDTO.builder().method("POST").path("/api/strategy/scan-now").description("Strategy scan now").build(),
            UiEndpointDTO.builder().method("GET").path("/api/signals/recent").description("Recent signals").build(),
            UiEndpointDTO.builder().method("GET").path("/api/watchlist").description("Watchlist entries").build(),
            UiEndpointDTO.builder().method("POST").path("/api/watchlist").description("Add watchlist entry").build(),
            UiEndpointDTO.builder().method("DELETE").path("/api/watchlist/{id}").description("Delete watchlist entry").build(),
            UiEndpointDTO.builder().method("GET").path("/api/broker/status").description("Broker status").build(),
            UiEndpointDTO.builder().method("GET").path("/api/dashboard").description("Dashboard aggregate").build(),
            UiEndpointDTO.builder().method("GET").path("/api/strategy/signals").description("All signals").build(),
            UiEndpointDTO.builder().method("GET").path("/api/strategy/signals/pending").description("Pending signals").build(),
            UiEndpointDTO.builder().method("POST").path("/api/strategy/mode?mode=PAPER|LIVE").description("Toggle strategy mode").build(),
            UiEndpointDTO.builder().method("GET").path("/api/strategy/mode").description("Get strategy mode").build(),
            UiEndpointDTO.builder().method("GET").path("/api/performance/metrics").description("Full metrics").build(),
            UiEndpointDTO.builder().method("GET").path("/api/performance/today-pnl").description("Today P&L").build(),
            UiEndpointDTO.builder().method("GET").path("/api/performance/unrealized-pnl").description("Open P&L").build(),
            UiEndpointDTO.builder().method("GET").path("/api/performance/win-rate").description("Win rate").build(),
            UiEndpointDTO.builder().method("GET").path("/api/performance/max-drawdown").description("Max drawdown").build(),
            UiEndpointDTO.builder().method("GET").path("/api/performance/profit-factor").description("Profit factor").build(),
            UiEndpointDTO.builder().method("GET").path("/api/performance/sharpe-ratio").description("Sharpe ratio").build(),
            UiEndpointDTO.builder().method("GET").path("/api/performance/roi").description("ROI").build(),
            UiEndpointDTO.builder().method("GET").path("/api/performance/equity-curve?type=PAPER|LIVE").description("Equity curve").build(),
            UiEndpointDTO.builder().method("GET").path("/api/performance/daily-pnl?from=YYYY-MM-DD&to=YYYY-MM-DD&type=PAPER|LIVE").description("Daily realized P&L").build(),
            UiEndpointDTO.builder().method("GET").path("/api/performance/monthly-pnl?year=YYYY&type=PAPER|LIVE").description("Monthly realized P&L").build(),
            UiEndpointDTO.builder().method("GET").path("/api/backtest/runs?page=0&size=20").description("Backtest runs list").build(),
            UiEndpointDTO.builder().method("GET").path("/api/backtest/runs/{id}").description("Backtest run details").build(),
            UiEndpointDTO.builder().method("GET").path("/api/risk/status").description("Risk status").build(),
            UiEndpointDTO.builder().method("POST").path("/api/risk/emergency-stop").description("Emergency stop").build(),
            UiEndpointDTO.builder().method("GET").path("/api/risk/correlation-matrix").description("Correlation matrix").build(),
            UiEndpointDTO.builder().method("GET").path("/api/guard/state").description("Guard safe mode state").build(),
            UiEndpointDTO.builder().method("GET").path("/api/guard/status").description("Guard safe mode status alias").build(),
            UiEndpointDTO.builder().method("POST").path("/api/guard/clear").description("Clear guard safe mode").build()
        );

        Map<String, List<String>> entityFields = new LinkedHashMap<>();
        entityFields.put("User", List.of(
            "id", "username", "email", "role", "availableFunds", "totalInvested", "currentValue",
            "enabled", "createdAt", "lastLogin", "fyersId", "fyersToken", "fyersConnected"
        ));
        entityFields.put("UserProfileDTO", List.of(
            "name", "availableFunds", "availableRealFunds", "availablePaperFunds", "totalInvested",
            "currentValue", "todaysPnl", "holdings"
        ));
        entityFields.put("HoldingDTO", List.of("symbol", "quantity", "avgPrice", "currentPrice", "pnl", "pnlPercent"));
        entityFields.put("Trade", List.of(
            "id", "symbol", "tradeType", "quantity", "entryPrice", "exitPrice", "entryTime", "exitTime",
            "stopLoss", "currentStopLoss", "atr", "highestPrice", "isPaperTrade", "status", "exitReason",
            "breakevenMoved", "realizedPnl"
        ));
        entityFields.put("PaperPositionDTO", List.of("symbol", "quantity", "entryPrice", "ltp", "pnl", "pnlPercent"));
        entityFields.put("PaperStatsDTO", List.of(
            "totalTrades", "winningTrades", "losingTrades", "winRate", "totalProfit", "totalLoss",
            "netPnl", "profitFactor"
        ));
        entityFields.put("SignalDTO", List.of("id", "symbol", "signalScore", "grade", "entryPrice", "scanTime", "hasEntrySignal"));
        entityFields.put("PerformanceMetrics", List.of(
            "totalTrades", "winningTrades", "losingTrades", "winRate", "netProfit", "averageWin",
            "averageLoss", "profitFactor", "expectancy", "maxDrawdown", "longestWinStreak",
            "longestLossStreak", "lastTradeTime", "lastTradeSymbol"
        ));
        entityFields.put("PnlSeriesResponse", List.of("type", "granularity", "series"));
        entityFields.put("PnlSeriesPoint", List.of("period", "pnl"));
        entityFields.put("BacktestRunSummary", List.of("id", "symbol", "timeframe", "startTime", "endTime", "createdAt"));
        entityFields.put("BacktestRunsResponse", List.of("runs", "page", "size", "totalElements"));
        entityFields.put("RiskStatus", List.of("equity", "openPositions"));
        entityFields.put("EmergencyStopResponse", List.of("message", "closedTrades", "globalHalt", "timestamp"));

        UiConfigDTO config = UiConfigDTO.builder()
            .apiBaseUrl(apiBaseUrl)
            .wsBaseUrl(wsBaseUrl)
            .endpoints(endpoints)
            .entityFields(entityFields)
            .build();

        return ResponseEntity.ok(config);
    }
}
