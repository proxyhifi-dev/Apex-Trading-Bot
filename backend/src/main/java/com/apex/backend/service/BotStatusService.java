package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.dto.BotStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class BotStatusService {

    private final StrategyConfig strategyConfig;
    private final BroadcastService broadcastService;

    private final AtomicReference<String> status = new AtomicReference<>("STOPPED");
    private final AtomicReference<String> lastError = new AtomicReference<>(null);
    private final AtomicInteger scannedStocks = new AtomicInteger(0);
    private final AtomicInteger totalStocks = new AtomicInteger(0);

    private volatile LocalDateTime lastScanTime;
    private volatile LocalDateTime nextScanTime;

    public void markRunning() {
        status.set("RUNNING");
        broadcast();
    }

    public void markPaused(String reason) {
        status.set("PAUSED");
        lastError.set(reason);
        broadcast();
    }

    public void markStopped(String reason) {
        status.set("STOPPED");
        lastError.set(reason);
        broadcast();
    }

    public void setLastScanTime(LocalDateTime time) {
        this.lastScanTime = time;
        broadcast();
    }

    public void setNextScanTime(LocalDateTime time) {
        this.nextScanTime = time;
        broadcast();
    }

    public void setTotalStocks(int total) {
        totalStocks.set(total);
        broadcast();
    }

    public void incrementScannedStocks() {
        scannedStocks.incrementAndGet();
    }

    public void resetScanProgress() {
        scannedStocks.set(0);
    }

    public void setLastError(String error) {
        lastError.set(error);
        broadcast();
    }

    public BotStatusResponse getStatus() {
        return BotStatusResponse.builder()
                .status(status.get())
                .lastScanTime(lastScanTime)
                .nextScanTime(nextScanTime)
                .scannedStocks(scannedStocks.get())
                .totalStocks(totalStocks.get())
                .strategyName(strategyConfig.getStrategy().getName())
                .lastError(lastError.get())
                .build();
    }

    public void broadcast() {
        broadcastService.broadcastBotStatus(getStatus());
    }
}
