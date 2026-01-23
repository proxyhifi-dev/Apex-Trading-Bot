package com.apex.backend.repository;

import com.apex.backend.model.TradingStrategy;
import com.apex.backend.model.WatchlistStock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WatchlistStockRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private WatchlistStockRepository watchlistStockRepository;

    @Test
    void findActiveSymbolsByStrategyIdReturnsActiveSymbols() {
        TradingStrategy strategy = entityManager.persist(TradingStrategy.builder()
                .name("Momentum")
                .active(true)
                .build());
        TradingStrategy otherStrategy = entityManager.persist(TradingStrategy.builder()
                .name("Breakout")
                .active(true)
                .build());

        entityManager.persist(WatchlistStock.builder()
                .symbol("NSE:AAA")
                .active(true)
                .strategy(strategy)
                .build());
        entityManager.persist(WatchlistStock.builder()
                .symbol("NSE:BBB")
                .active(false)
                .strategy(strategy)
                .build());
        entityManager.persist(WatchlistStock.builder()
                .symbol("NSE:CCC")
                .active(true)
                .strategy(otherStrategy)
                .build());
        entityManager.flush();

        List<String> symbols = watchlistStockRepository.findActiveSymbolsByStrategyId(strategy.getId());

        assertThat(symbols).containsExactly("NSE:AAA");
    }
}
