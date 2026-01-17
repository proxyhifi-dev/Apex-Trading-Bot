package com.apex.backend.service;

import com.apex.backend.model.BotState;
import com.apex.backend.repository.BotStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BotOpsService {

    private final BotStateRepository botStateRepository;
    private final BotScheduler botScheduler;
    private final BotStatusService botStatusService;

    @Transactional
    public BotState start(Long userId) {
        botScheduler.initialize();
        BotState state = getOrCreate(userId);
        state.setRunning(true);
        state.setLastError(null);
        state.setLastErrorAt(null);
        state.setThreadAlive(botScheduler.isBotReady());
        return botStateRepository.save(state);
    }

    @Transactional
    public BotState stop(Long userId) {
        BotState state = getOrCreate(userId);
        state.setRunning(false);
        state.setThreadAlive(false);
        state.setLastError("Stopped by user");
        state.setLastErrorAt(Instant.now());
        botStatusService.markStopped("Stopped by user");
        return botStateRepository.save(state);
    }

    @Transactional
    public BotState reloadConfig(Long userId) {
        BotState state = getOrCreate(userId);
        state.setLastError(null);
        state.setLastErrorAt(null);
        return botStateRepository.save(state);
    }

    @Transactional
    public BotState updateHealth(Long userId, Instant lastScanAt, Instant nextScanAt, String lastError, Instant lastErrorAt) {
        BotState state = getOrCreate(userId);
        state.setLastScanAt(lastScanAt);
        state.setNextScanAt(nextScanAt);
        state.setLastError(lastError);
        state.setLastErrorAt(lastErrorAt);
        state.setThreadAlive(botScheduler.isBotReady());
        return botStateRepository.save(state);
    }

    public BotState getState(Long userId) {
        return getOrCreate(userId);
    }

    private BotState getOrCreate(Long userId) {
        return botStateRepository.findByUserId(userId)
                .orElseGet(() -> botStateRepository.save(BotState.builder()
                        .userId(userId)
                        .running(false)
                        .threadAlive(false)
                        .queueDepth(0)
                        .build()));
    }
}
