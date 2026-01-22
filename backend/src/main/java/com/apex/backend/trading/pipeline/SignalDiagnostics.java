package com.apex.backend.trading.pipeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalDiagnostics {

    private boolean trendPass;
    private boolean volumePass;
    private boolean breakoutPass;
    private boolean rsiPass;
    private boolean adxPass;
    private boolean atrPass;
    private boolean momentumPass;
    private boolean squeezePass;
    @Builder.Default
    private List<ScanRejectReason> rejectionReasons = new ArrayList<>();

    public void addRejectionReason(ScanRejectReason reason) {
        if (reason != null) {
            rejectionReasons.add(reason);
        }
    }

    public static SignalDiagnostics withReason(ScanRejectReason reason) {
        SignalDiagnostics diagnostics = new SignalDiagnostics();
        diagnostics.addRejectionReason(reason);
        return diagnostics;
    }
}
