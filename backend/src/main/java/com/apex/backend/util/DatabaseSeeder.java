package com.apex.backend.util;

import com.apex.backend.model.TradingStrategy;
import com.apex.backend.repository.TradingStrategyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "apex.seed-demo-data", havingValue = "true")
public class DatabaseSeeder implements CommandLineRunner {

    private final TradingStrategyRepository strategyRepo;

    @Override
    @Transactional // ✅ FIXED: Keeps entity managed during the entire seeding process
    public void run(String... args) {
        try {
            if (strategyRepo.count() == 0) {
                seedDatabase();
            } else {
                log.info("⏭️ Database already seeded, skipping...");
            }
        } catch (Exception e) {
            log.error("❌ DatabaseSeeder failed: {}", e.getMessage(), e);
        }
    }

    private void seedDatabase() {
        log.info("🌱 Seeding database...");
        try {
            TradingStrategy strategy = new TradingStrategy();
            strategy.setName("High-Alpha Momentum");
            strategy.setActive(true);
            strategy.setInitialCapital(BigDecimal.valueOf(100000.0));
            strategy.setMinEntryScore(70);
            // Default Indicator Params
            strategy.setRsiPeriod(14);
            strategy.setRsiNeutral(50.0);
            strategy.setRsiWeight(20);
            strategy.setMacdFastPeriod(12);
            strategy.setMacdSlowPeriod(26);
            strategy.setMacdSignalPeriod(9);
            strategy.setMacdWeight(20);
            strategy.setAdxPeriod(14);
            strategy.setAdxWeight(20);
            strategy.setBollingerPeriod(20);
            strategy.setBollingerStdDev(2.0);
            strategy.setSqueezeWeight(20);
            strategyRepo.save(strategy);
            log.info("✅ Strategy seeded successfully.");

        } catch (Exception e) {
            log.error("❌ Database seeding failed: {}", e.getMessage(), e);
        }
    }
}
