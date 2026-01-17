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
}
