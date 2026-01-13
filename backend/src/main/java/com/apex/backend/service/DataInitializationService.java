package com.apex.backend.service;

import com.apex.backend.model.User;
import com.apex.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data Initialization Service
 * Creates default test users on application startup
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataInitializationService implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Value("${apex.bootstrap.default-users:true}")
    private boolean defaultUsersEnabled;

    @Override
    public void run(String... args) {
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\bollu\\github\\Apex-Trading-Bot\\.cursor\\debug.log", true);
            fw.write("{\"id\":\"log_" + System.currentTimeMillis() + "_dataInitStart\",\"timestamp\":" + java.time.Instant.now().toEpochMilli() + ",\"location\":\"DataInitializationService.java:33\",\"message\":\"DataInitializationService.run started\",\"data\":{\"defaultUsersEnabled\":\"" + defaultUsersEnabled + "\",\"userRepoPresent\":\"" + (userRepository != null) + "\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"C\"}\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        try {
            if (!defaultUsersEnabled || isProdProfileActive()) {
                log.info("Default user initialization skipped (production or disabled).");
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\bollu\\github\\Apex-Trading-Bot\\.cursor\\debug.log", true);
                    fw.write("{\"id\":\"log_" + System.currentTimeMillis() + "_dataInitSkipped\",\"timestamp\":" + java.time.Instant.now().toEpochMilli() + ",\"location\":\"DataInitializationService.java:36\",\"message\":\"Data initialization skipped\",\"data\":{},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"C\"}\n");
                    fw.close();
                } catch (Exception e) {}
                // #endregion
                return;
            }
            // Create default admin user if not exists
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\bollu\\github\\Apex-Trading-Bot\\.cursor\\debug.log", true);
                fw.write("{\"id\":\"log_" + System.currentTimeMillis() + "_checkAdmin\",\"timestamp\":" + java.time.Instant.now().toEpochMilli() + ",\"location\":\"DataInitializationService.java:40\",\"message\":\"Checking if admin user exists\",\"data\":{},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\"}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            if (!userRepository.existsByUsername("admin")) {
                User admin = User.builder()
                    .username("admin")
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .email("admin@apextrading.com")
                    .role("ADMIN")
                    .availableFunds(BigDecimal.valueOf(500000.0))
                    .totalInvested(BigDecimal.ZERO)
                    .currentValue(BigDecimal.valueOf(500000.0))
                    .enabled(true)
                    .createdAt(LocalDateTime.now())
                    .build();
                userRepository.save(admin);
                log.info("✅ Default admin user created: username=admin, password=admin123");
            }

            // Create default test user if not exists
            if (!userRepository.existsByUsername("trader")) {
                User trader = User.builder()
                    .username("trader")
                    .passwordHash(passwordEncoder.encode("trader123"))
                    .email("trader@apextrading.com")
                    .role("USER")
                    .availableFunds(BigDecimal.valueOf(100000.0))
                    .totalInvested(BigDecimal.ZERO)
                    .currentValue(BigDecimal.valueOf(100000.0))
                    .enabled(true)
                    .createdAt(LocalDateTime.now())
                    .build();
                userRepository.save(trader);
                log.info("✅ Default trader user created: username=trader, password=trader123");
            }

            log.info("✅ Data initialization completed successfully");
            log.info("📊 Total users in database: {}", userRepository.count());
            
        } catch (Exception e) {
            log.error("❌ Data initialization failed", e);
        }
    }

    private boolean isProdProfileActive() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
