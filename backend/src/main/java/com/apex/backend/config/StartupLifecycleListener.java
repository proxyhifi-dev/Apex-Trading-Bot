package com.apex.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StartupLifecycleListener implements ApplicationListener<ApplicationFailedEvent> {

    @Override
    public void onApplicationEvent(ApplicationFailedEvent event) {
        Throwable exception = event.getException();
        Throwable root = rootCause(exception);
        log.error("FATAL Startup failure. Root cause: {}", root.getMessage(), exception);
        if (root.getSuppressed() != null && root.getSuppressed().length > 0) {
            for (Throwable suppressed : root.getSuppressed()) {
                log.error("FATAL Suppressed: {}", suppressed.getMessage(), suppressed);
            }
        }
    }

    @Component
    @Slf4j
    public static class StartupReadyListener implements ApplicationListener<ApplicationReadyEvent> {
        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            log.info("🚀 STARTUP COMPLETED — APP READY");
        }
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
