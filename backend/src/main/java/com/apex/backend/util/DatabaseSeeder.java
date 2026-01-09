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
        if (strategyRepo.count() == 0) {
            seedDatabase();
        } else {
            log.info("⏭️ Database already seeded, skipping...");
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

        } catch (Exception e) {
            log.error("❌ Database seeding failed: {}", e.getMessage(), e);
        }
    }
}
