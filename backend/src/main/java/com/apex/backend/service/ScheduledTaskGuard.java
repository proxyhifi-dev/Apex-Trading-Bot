package com.apex.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduledTaskGuard {

    private final AuditEventService auditEventService;

    public void run(String taskName, Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            log.error("Scheduled task failed task={}", taskName, t);
            HashMap<String, Object> metadata = new HashMap<>();
            metadata.put("task", taskName);
            metadata.put("error", t.getMessage());
            metadata.put("timestamp", Instant.now().toString());
            auditEventService.recordEvent(0L, "scheduler", "TASK_FAILED",
                    "Scheduled task failed: " + taskName, metadata);
        }
    }
}
