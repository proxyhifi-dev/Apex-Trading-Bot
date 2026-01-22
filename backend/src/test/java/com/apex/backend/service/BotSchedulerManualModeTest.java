package com.apex.backend.service;

import com.apex.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = {
        "jwt.secret=01234567890123456789012345678901",
        "apex.scanner.enabled=false",
        "apex.scanner.mode=MANUAL"
})
class BotSchedulerManualModeTest {

    @Autowired
    private BotScheduler botScheduler;

    @MockBean
    private com.apex.backend.service.risk.CircuitBreakerService tradingGuardService;

    @MockBean
    private ScannerOrchestrator scannerOrchestrator;

    @MockBean
    private ExitManager exitManager;

    @MockBean
    private BotStatusService botStatusService;

    @MockBean
    private StrategyHealthService strategyHealthService;

    @MockBean
    private SystemGuardService systemGuardService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private WatchlistService watchlistService;

    @Test
    void schedulerDoesNotRunInManualMode() {
        botScheduler.runBotCycle();
        verifyNoInteractions(scannerOrchestrator);
    }
}
