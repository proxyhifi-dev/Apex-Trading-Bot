package com.apex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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

    private Instant lastReconcileAt;

    private Instant lastMismatchAt;

    private String lastMismatchReason;

    private Instant updatedAt;
}
