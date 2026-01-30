package com.apex.backend.service;

import com.apex.backend.entity.SystemGuardState;
import com.apex.backend.repository.SystemGuardStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SystemGuardService {

    private static final long SINGLETON_ID = 1L;

    private final SystemGuardStateRepository systemGuardStateRepository;
    private final RiskEventService riskEventService;

    @Transactional
    public SystemGuardState getState() {
        return systemGuardStateRepository.findById(SINGLETON_ID)
                .orElseGet(() -> systemGuardStateRepository.save(SystemGuardState.builder()
                        .id(SINGLETON_ID)
                        .safeMode(false)
                        .crisisMode(false)
                        .emergencyMode(false)
                        .updatedAt(Instant.now())
                        .build()));
    }

    @Transactional
    public SystemGuardState setSafeMode(boolean safeMode, String reason, Instant mismatchAt) {
        SystemGuardState state = getState();
        state.setSafeMode(safeMode);
        if (safeMode) {
            state.setLastMismatchAt(mismatchAt != null ? mismatchAt : Instant.now());
            state.setLastMismatchReason(reason);
            riskEventService.record(0L, "GUARD_SAFE_MODE", reason, "enteredAt=" + state.getLastMismatchAt());
        }
        state.setUpdatedAt(Instant.now());
        return systemGuardStateRepository.save(state);
    }

    @Transactional
    public SystemGuardState clearSafeMode() {
        SystemGuardState state = getState();
        state.setSafeMode(false);
        state.setLastMismatchReason(null);
        state.setUpdatedAt(Instant.now());
        riskEventService.record(0L, "GUARD_SAFE_MODE_EXIT", "Safe mode cleared", null);
        return systemGuardStateRepository.save(state);
    }

    @Transactional
    public SystemGuardState updateReconcileTimestamp(Instant reconcileAt) {
        SystemGuardState state = getState();
        state.setLastReconcileAt(reconcileAt != null ? reconcileAt : Instant.now());
        state.setUpdatedAt(Instant.now());
        return systemGuardStateRepository.save(state);
    }

    @Transactional
    public SystemGuardState setCrisisMode(boolean crisisMode, String reason, String detail, Instant startedAt, Instant until) {
        SystemGuardState state = getState();
        state.setCrisisMode(crisisMode);
        if (crisisMode) {
            state.setCrisisReason(reason);
            state.setCrisisDetail(detail);
            state.setCrisisStartedAt(startedAt != null ? startedAt : Instant.now());
            state.setCrisisUntil(until);
        } else {
            state.setCrisisReason(null);
            state.setCrisisDetail(null);
            state.setCrisisStartedAt(null);
            state.setCrisisUntil(null);
        }
        state.setUpdatedAt(Instant.now());
        return systemGuardStateRepository.save(state);
    }

    @Transactional
    public SystemGuardState clearCrisisModeIfExpired() {
        SystemGuardState state = getState();
        if (!state.isCrisisMode()) {
            return state;
        }
        Instant now = Instant.now();
        if (state.getCrisisUntil() != null && now.isAfter(state.getCrisisUntil())) {
            state.setCrisisMode(false);
            state.setCrisisReason(null);
            state.setCrisisDetail(null);
            state.setCrisisStartedAt(null);
            state.setCrisisUntil(null);
            state.setUpdatedAt(now);
            return systemGuardStateRepository.save(state);
        }
        return state;
    }

    @Transactional(readOnly = true)
    public boolean isCrisisModeActive() {
        SystemGuardState state = getState();
        if (!state.isCrisisMode()) {
            return false;
        }
        if (state.getCrisisUntil() != null && Instant.now().isAfter(state.getCrisisUntil())) {
            return false;
        }
        return true;
    }

    @Transactional
    public SystemGuardState setEmergencyMode(boolean emergencyMode, String reason, Instant startedAt) {
        SystemGuardState state = getState();
        state.setEmergencyMode(emergencyMode);
        if (emergencyMode) {
            state.setEmergencyReason(reason);
            state.setEmergencyStartedAt(startedAt != null ? startedAt : Instant.now());
            riskEventService.record(0L, "SYSTEM_EMERGENCY", reason, "startedAt=" + state.getEmergencyStartedAt());
        } else {
            state.setEmergencyReason(null);
            state.setEmergencyStartedAt(null);
        }
        state.setUpdatedAt(Instant.now());
        return systemGuardStateRepository.save(state);
    }

    @Transactional(readOnly = true)
    public boolean isEmergencyModeActive() {
        return getState().isEmergencyMode();
    }

    @Transactional(readOnly = true)
    public boolean isTradingBlocked() {
        SystemGuardState state = getState();
        return state.isSafeMode() || state.isEmergencyMode() || isCrisisModeActive();
    }
}
