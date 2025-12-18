package com.apex.backend.controller;

import com.apex.backend.service.BotStrategy;
import com.apex.backend.service.FyersService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200") // Allow Angular access
@RequiredArgsConstructor
public class TradeController {

    private final FyersService fyersService;
    private final BotStrategy botStrategy;
    // Status check for UI
    @GetMapping("/status")
    public Map<String, Object> getSystemStatus() {
        String quoteResponse = fyersService.getQuote(null);
        boolean isConnected = (quoteResponse != null);
        return Map.of(
                "status", isConnected ? "ONLINE" : "OFFLINE",
                "message", isConnected ? "Token Active" : "Need Token Exchange",
                "botActive", botStrategy.getStatus(),
                "botState", botStrategy.getBotState(),
                "performance", botStrategy.getPerformance()
        );
    }

    // --- Core Bot Endpoints ---

    @GetMapping("/logs")
    public List<String> getLogs() {
        return botStrategy.getLiveLogs();
    }

    @GetMapping("/paper-orders")
    public List<Map<String, Object>> getPaperOrders() {
        return botStrategy.getPaperOrders();
    }

    // ✅ FIXED: Updated to match new FyersService signature
    @GetMapping("/placeOrder")
    public String placeOrder(@RequestParam String symbol,
                             @RequestParam int qty,
                             @RequestParam String side, // Now expects "BUY" or "SELL"
                             @RequestParam(defaultValue = "MARKET") String type,
                             @RequestParam(defaultValue = "0.0") double price,
                             @RequestParam(defaultValue = "true") boolean paperMode) {

        fyersService.placeOrder(symbol, qty, side, type, price, paperMode);
        return "Order Command Issued: " + side + " " + qty + " " + symbol;
    }

    // START COMMAND (dynamic symbol, strategy, mode)
    @GetMapping("/bot/start")
    public String startBot(
            @RequestParam(defaultValue = "NSE:SBIN-EQ") String symbol,
            @RequestParam(defaultValue = "MOMENTUM") String strategy,
            @RequestParam(defaultValue = "true") boolean paperMode
    ) {
        botStrategy.startBot(symbol, strategy, paperMode);
        return "Bot Started using " + strategy + " on " + symbol + " | Paper: " + paperMode;
    }

    @GetMapping("/bot/stop")
    public String stopBot() {
        botStrategy.stopBot();
        return "Bot Stopped";
    }

    // Quote endpoint for UI
    @GetMapping("/quote")
    public String getQuote(@RequestParam String symbol) {
        return fyersService.getQuote(symbol);
    }
}