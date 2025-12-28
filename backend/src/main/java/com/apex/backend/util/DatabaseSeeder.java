package com.apex.backend.util;

import com.apex.backend.model.*;
import com.apex.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final TradingStrategyRepository strategyRepo;
    private final WatchlistStockRepository watchlistRepo;
    private final StockScreeningResultRepository screeningRepo;

    @Override
    public void run(String... args) {
        if (strategyRepo.count() == 0) {
            seedDatabase();
        } else {
            log.info("⏭️ Database already seeded, skipping...");
        }
    }

    private void seedDatabase() {
        log.info("🌱 Seeding database for demo...");
        try {
            TradingStrategy strategy = new TradingStrategy();
            strategy.setName("High-Alpha Momentum");
            strategy.setActive(true);  // ✅ CHANGED FROM setIsActive
            strategy.setInitialCapital(100000.0);
            strategy.setMinEntryScore(70);
            strategy.setMacdWeight(25);
            strategy.setAdxWeight(25);
            strategy.setRsiWeight(20);
            strategy.setSqueezeWeight(30);
            strategy.setBollingerPeriod(20);
            strategy.setBollingerStdDev(2.0);
            strategy.setRsiPeriod(14);
            strategy.setAdxPeriod(14);
            strategy.setRsiNeutral(50.0);

            strategy = strategyRepo.save(strategy);
            log.info("✅ Strategy created: {}", strategy.getName());

            String[] symbols = {
                    "NSE:RELIANCE-EQ",
                    "NSE:TCS-EQ",
                    "NSE:INFY-EQ",
                    "NSE:HDFCBANK-EQ",
                    "NSE:ICICIBANK-EQ"
            };

            for (String symbol : symbols) {
                WatchlistStock stock = new WatchlistStock();
                stock.setSymbol(symbol);
                stock.setStrategy(strategy);
                stock.setActive(true);  // ✅ CHANGED FROM setIsActive
                watchlistRepo.save(stock);
            }

            log.info("✅ Watchlist created: {} stocks", symbols.length);

            StockScreeningResult signal1 = StockScreeningResult.builder()
                    .strategy(strategy)
                    .symbol("NSE:RELIANCE-EQ")
                    .scanTime(LocalDateTime.now())
                    .currentPrice(2450.50)
                    .signalScore(88)
                    .hasEntrySignal(true)
                    .approvalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                    .build();
            screeningRepo.save(signal1);

            StockScreeningResult signal2 = StockScreeningResult.builder()
                    .strategy(strategy)
                    .symbol("NSE:TCS-EQ")
                    .scanTime(LocalDateTime.now())
                    .currentPrice(3520.75)
                    .signalScore(76)
                    .hasEntrySignal(true)
                    .approvalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                    .build();
            screeningRepo.save(signal2);

            StockScreeningResult signal3 = StockScreeningResult.builder()
                    .strategy(strategy)
                    .symbol("NSE:INFY-EQ")
                    .scanTime(LocalDateTime.now())
                    .currentPrice(1450.25)
                    .signalScore(82)
                    .hasEntrySignal(true)
                    .approvalStatus(StockScreeningResult.ApprovalStatus.PENDING)
                    .build();
            screeningRepo.save(signal3);

            log.info("✅ Test signals created: 3 signals (RELIANCE, TCS, INFY)");
            log.info("🎉 Database seeding complete! Ready for demo!");

        } catch (Exception e) {
            log.error("❌ Database seeding failed: {}", e.getMessage(), e);
        }
    }
}
