package com.apex.backend.controller;

import com.apex.backend.dto.BacktestRequest;
import com.apex.backend.dto.BacktestResponse;
import com.apex.backend.dto.BacktestRunSummary;
import com.apex.backend.dto.ValidationRequest;
import com.apex.backend.dto.ValidationResponse;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.model.BacktestResult;
import com.apex.backend.model.ValidationRun;
import com.apex.backend.repository.BacktestResultRepository;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.BacktestService;
import com.apex.backend.service.BacktestValidationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
public class BacktestController {

    private final BacktestService backtestService;
    private final BacktestResultRepository backtestResultRepository;
    private final BacktestValidationService backtestValidationService;

    @PostMapping("/run")
    public BacktestResponse run(@Valid @RequestBody BacktestRequest request,
                                @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        BacktestResult result = backtestService.runBacktest(userId, request.symbol(), request.timeframe(), request.bars());
        return new BacktestResponse(result.getId(), result.getSymbol(), result.getTimeframe(), result.getMetricsJson());
    }

    @GetMapping("/runs")
    public BacktestRunsResponse listRuns(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        Page<BacktestResult> results = backtestResultRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
        List<BacktestRunSummary> runs = results.stream()
                .map(result -> new BacktestRunSummary(
                        result.getId(),
                        result.getSymbol(),
                        result.getTimeframe(),
                        result.getStartTime(),
                        result.getEndTime(),
                        result.getCreatedAt()
                ))
                .toList();
        return new BacktestRunsResponse(runs, results.getNumber(), results.getSize(), results.getTotalElements());
    }

    @GetMapping("/runs/{id}")
    public BacktestResponse runDetails(@PathVariable Long id,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        return result(id, principal);
    }

    @GetMapping("/results/{id}")
    public BacktestResponse result(@PathVariable Long id,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        BacktestResult result = backtestResultRepository.findByIdAndUserId(id, userId).orElseThrow();
        return new BacktestResponse(result.getId(), result.getSymbol(), result.getTimeframe(), result.getMetricsJson());
    }

    @PostMapping("/validate")
    public ValidationResponse validate(@Valid @RequestBody ValidationRequest request,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        ValidationRun run = backtestValidationService.validate(userId, request.backtestResultId());
        return new ValidationResponse(run.getId(), run.getBacktestResultId(), run.getMetricsJson());
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }

    public record BacktestRunsResponse(
            List<BacktestRunSummary> runs,
            int page,
            int size,
            long totalElements
    ) {}
}
