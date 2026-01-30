package com.apex.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AsyncDelayService {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "async-delay");
        thread.setDaemon(true);
        return thread;
    });

    public void awaitMillis(long millis) {
        await(Duration.ofMillis(millis));
    }

    public void await(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.schedule(() -> future.complete(null), duration.toMillis(), TimeUnit.MILLISECONDS);
        future.join();
    }
}
