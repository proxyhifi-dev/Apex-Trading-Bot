package com.apex.backend.controller;

import com.apex.backend.dto.ApiErrorResponse;
import com.apex.backend.model.Candle;
import com.apex.backend.service.FyersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketDataController {

    private final FyersService fyersService;

    @GetMapping("/ltp")
    public ResponseEntity<?> getLtp(@RequestParam String symbols) {
        try {
            List<String> symbolList = List.of(symbols.split(","));
            Map<String, Double> ltpMap = fyersService.getLtpBatch(symbolList);
            return ResponseEntity.ok(ltpMap);
        } catch (Exception e) {
            log.error("Failed to fetch LTP", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("Failed to fetch LTP", e.getMessage()));
        }
    }

    @GetMapping("/candles")
    public ResponseEntity<?> getCandles(@RequestParam String symbol,
                                        @RequestParam(defaultValue = "5") String tf,
                                        @RequestParam(defaultValue = "200") int count) {
        try {
            List<Candle> candles = fyersService.getHistoricalData(symbol, count, tf);
            return ResponseEntity.ok(candles);
        } catch (Exception e) {
            log.error("Failed to fetch candles", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("Failed to fetch candles", e.getMessage()));
        }
    }
}
