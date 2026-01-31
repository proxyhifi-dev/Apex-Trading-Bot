package com.apex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "system_guard_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemGuardState {

    @Id
    private Long id;

    @Column(nullable = false)
    private boolean safeMode;

    @Column(nullable = false)
    private boolean crisisMode;

    @Column(nullable = false)
    private boolean emergencyMode;

    private String emergencyReason;

    private Instant emergencyStartedAt;

    @Column(nullable = false)
    private boolean panicMode;

    private String panicReason;

    private Instant panicStartedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SystemMode systemMode;

    private String lastPanicReason;

    private Instant lastPanicAt;

    private String crisisReason;

    private String crisisDetail;

    private Instant crisisStartedAt;

    private Instant crisisUntil;

    private Instant lastReconcileAt;

    private Instant lastMismatchAt;

    private String lastMismatchReason;

    private Instant updatedAt;

    public enum SystemMode {
        RUNNING,
        SAFE,
        STOPPED,
        PANIC
    }
}
