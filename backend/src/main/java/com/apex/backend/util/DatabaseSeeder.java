package com.apex.backend.util;

import com.apex.backend.model.*;
import com.apex.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final TradingStrategyRepository strategyRepo;
    private final WatchlistStockRepository watchlistRepo;
    private final StockScreeningResultRepository screeningRepo;
    private final UserRepository userRepository;

    @Override
    @Transactional // ✅ FIXED: Keeps entity managed during the entire seeding process
    public void run(String... args) {
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\bollu\\github\\Apex-Trading-Bot\\.cursor\\debug.log", true);
            fw.write("{\"id\":\"log_" + System.currentTimeMillis() + "_seederStart\",\"timestamp\":" + java.time.Instant.now().toEpochMilli() + ",\"location\":\"DatabaseSeeder.java:26\",\"message\":\"DatabaseSeeder.run started\",\"data\":{\"strategyRepoPresent\":\"" + (strategyRepo != null) + "\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\"}\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        try {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\bollu\\github\\Apex-Trading-Bot\\.cursor\\debug.log", true);
                long count = strategyRepo.count();
                fw.write("{\"id\":\"log_" + System.currentTimeMillis() + "_seederCount\",\"timestamp\":" + java.time.Instant.now().toEpochMilli() + ",\"location\":\"DatabaseSeeder.java:28\",\"message\":\"Checking strategy count\",\"data\":{\"strategyCount\":\"" + count + "\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\"}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            if (strategyRepo.count() == 0) {
                seedDatabase();
            } else {
                log.info("⏭️ Database already seeded, skipping...");
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\bollu\\github\\Apex-Trading-Bot\\.cursor\\debug.log", true);
                    fw.write("{\"id\":\"log_" + System.currentTimeMillis() + "_seederSkipped\",\"timestamp\":" + java.time.Instant.now().toEpochMilli() + ",\"location\":\"DatabaseSeeder.java:31\",\"message\":\"Database seeding skipped (already seeded)\",\"data\":{},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\"}\n");
                    fw.close();
                } catch (Exception e) {}
                // #endregion
            }
        } catch (Exception e) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\bollu\\github\\Apex-Trading-Bot\\.cursor\\debug.log", true);
                fw.write("{\"id\":\"log_" + System.currentTimeMillis() + "_seederError\",\"timestamp\":" + java.time.Instant.now().toEpochMilli() + ",\"location\":\"DatabaseSeeder.java:35\",\"message\":\"DatabaseSeeder.run failed\",\"data\":{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "\\\"").replace("\n", " ") : "null") + "\",\"errorType\":\"" + e.getClass().getName() + "\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\"}\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
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

            strategy = strategyRepo.save(strategy);

            // Add Nifty 50 sample
            String[] symbols = {
                    "NSE:RELIANCE-EQ", "NSE:TCS-EQ", "NSE:INFY-EQ",
                    "NSE:HDFCBANK-EQ", "NSE:ICICIBANK-EQ"
            };

            for (String symbol : symbols) {
                WatchlistStock stock = new WatchlistStock();
                stock.setSymbol(symbol);
                stock.setStrategy(strategy);
                stock.setActive(true);
                watchlistRepo.save(stock);
            }

            Long ownerUserId = userRepository.findTopByOrderByIdAsc()
                    .map(User::getId)
                    .orElse(null);
            if (ownerUserId == null) {
                log.warn("⚠️ Skipping signal seeding because no users exist yet.");
                return;
            }

            // Seed Sample Signals
            StockScreeningResult signal1 = StockScreeningResult.builder()
                    .strategy(strategy)
                    .userId(ownerUserId)
                    .symbol("NSE:RELIANCE-EQ")
                    .scanTime(LocalDateTime.now())
                    .entryPrice(BigDecimal.valueOf(2450.50))
                    .signalScore(88)
                    .grade("A+")
                    .approvalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                    .analysisReason("Breakout")
                    .build();
            screeningRepo.save(signal1);

            StockScreeningResult signal2 = StockScreeningResult.builder()
                    .strategy(strategy)
                    .userId(ownerUserId)
                    .symbol("NSE:TCS-EQ")
                    .scanTime(LocalDateTime.now())
                    .entryPrice(BigDecimal.valueOf(3520.75))
                    .signalScore(76)
                    .grade("B")
                    .approvalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                    .analysisReason("RSI Divergence")
                    .build();
            screeningRepo.save(signal2);

            log.info("✅ Database seeded successfully.");
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\bollu\\github\\Apex-Trading-Bot\\.cursor\\debug.log", true);
                fw.write("{\"id\":\"log_" + System.currentTimeMillis() + "_seedSuccess\",\"timestamp\":" + java.time.Instant.now().toEpochMilli() + ",\"location\":\"DatabaseSeeder.java:107\",\"message\":\"Database seeding completed successfully\",\"data\":{},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\"}\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion

        } catch (Exception e) {
            log.error("❌ Database seeding failed: {}", e.getMessage(), e);
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\bollu\\github\\Apex-Trading-Bot\\.cursor\\debug.log", true);
                fw.write("{\"id\":\"log_" + System.currentTimeMillis() + "_seedError\",\"timestamp\":" + java.time.Instant.now().toEpochMilli() + ",\"location\":\"DatabaseSeeder.java:110\",\"message\":\"Database seeding failed\",\"data\":{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "\\\"").replace("\n", " ") : "null") + "\",\"errorType\":\"" + e.getClass().getName() + "\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\"}\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
        }
    }
}
