package com.apex.backend.service;

import com.apex.backend.service.secrets.SecretsManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class FyersConfigGuard implements ApplicationRunner {

    private final SystemGuardService systemGuardService;
    private final BrokerStatusService brokerStatusService;
    private final AuditEventService auditEventService;
    private final SecretsManagerService secretsManagerService;

    @Value("${fyers.api.app-id:}")
    private String appId;

    @Value("${fyers.api.secret-key:}")
    private String secretKey;

    @Value("${fyers.redirect-uri:}")
    private String redirectUri;

    @Override
    public void run(ApplicationArguments args) {
        try {
            appId = secretsManagerService.resolve("FYERS_API_APP_ID", appId);
            secretKey = secretsManagerService.resolve("FYERS_API_SECRET_KEY", secretKey);
            redirectUri = secretsManagerService.resolve("FYERS_REDIRECT_URI", redirectUri);

            List<String> missing = missingKeys(appId, secretKey, redirectUri);
            if (!missing.isEmpty()) {
                String reason = "FYERS config missing: " + String.join(", ", missing);
                log.error(reason);
                systemGuardService.setSafeMode(true, reason, Instant.now());
                brokerStatusService.markDegraded("FYERS", "CONFIG_MISSING");
                HashMap<String, Object> metadata = new HashMap<>();
                metadata.put("missing", missing);
                auditEventService.recordEvent(0L, "broker_config", "FYERS_CONFIG_MISSING",
                        "FYERS broker config missing", metadata);
            }
        } catch (Exception e) {
            log.error("FYERS config guard failed", e);
            systemGuardService.setSafeMode(true, "FYERS_CONFIG_GUARD_ERROR", Instant.now());
        }
    }

    private List<String> missingKeys(String appId, String secretKey, String redirectUri) {
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        if (isBlank(appId)) {
            missing.add("FYERS_API_APP_ID");
        }
        if (isBlank(secretKey)) {
            missing.add("FYERS_API_SECRET_KEY");
        }
        if (isBlank(redirectUri)) {
            missing.add("FYERS_REDIRECT_URI");
        }
        return missing;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
