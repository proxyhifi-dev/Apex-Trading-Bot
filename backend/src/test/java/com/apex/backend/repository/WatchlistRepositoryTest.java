package com.apex.backend.repository;

import com.apex.backend.model.Watchlist;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WatchlistRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Test
    void findByUserIdAndIsDefaultTrueIsUserScoped() {
        Watchlist user1Default = entityManager.persist(Watchlist.builder()
                .userId(10L)
                .name("Default")
                .isDefault(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
        entityManager.persist(Watchlist.builder()
                .userId(11L)
                .name("Default")
                .isDefault(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
        entityManager.flush();

        assertThat(watchlistRepository.findByUserIdAndIsDefaultTrue(10L))
                .contains(user1Default);
        assertThat(watchlistRepository.findByUserIdAndIsDefaultTrue(12L))
                .isEmpty();
    }
}
