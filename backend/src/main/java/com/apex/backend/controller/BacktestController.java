package com.apex.backend.controller;

import com.apex.backend.dto.BacktestRequest;
import com.apex.backend.dto.BacktestResponse;
import com.apex.backend.model.BacktestResult;
import com.apex.backend.repository.BacktestResultRepository;
import com.apex.backend.service.BacktestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
public class BacktestController {

    private final BacktestService backtestService;
    private final BacktestResultRepository backtestResultRepository;

    @PostMapping("/run")
    public BacktestResponse run(@Valid @RequestBody BacktestRequest request) {
        BacktestResult result = backtestService.runBacktest(request.symbol(), request.timeframe(), request.bars());
        return new BacktestResponse(result.getId(), result.getSymbol(), result.getTimeframe(), result.getMetricsJson());
    }

    @GetMapping("/results/{id}")
    public BacktestResponse result(@PathVariable Long id) {
        BacktestResult result = backtestResultRepository.findById(id).orElseThrow();
        return new BacktestResponse(result.getId(), result.getSymbol(), result.getTimeframe(), result.getMetricsJson());
    }
}
