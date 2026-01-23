package com.apex.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "apex.scanner.scheduler-enabled", havingValue = "true")
public class ScannerCycleScheduler {

    private final BotScheduler botScheduler;

    @Scheduled(fixedDelayString = "${apex.scanner.interval}000")
    public void runCycle() {
        botScheduler.runScheduledCycle();
    }
}
