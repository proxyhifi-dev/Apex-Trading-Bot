package com.apex.backend.controller;

import com.apex.backend.model.MarketRegimeHistory;
import com.apex.backend.repository.MarketRegimeHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/regime")
@RequiredArgsConstructor
public class RegimeController {

    private final MarketRegimeHistoryRepository marketRegimeHistoryRepository;

    @GetMapping("/history")
    public List<MarketRegimeHistory> history(@RequestParam String symbol, @RequestParam(defaultValue = "5m") String timeframe) {
        return marketRegimeHistoryRepository.findTop200BySymbolAndTimeframeOrderByDetectedAtDesc(symbol, timeframe);
    }
}
