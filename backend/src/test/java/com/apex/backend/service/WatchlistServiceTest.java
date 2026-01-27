package com.apex.backend.service;

import com.apex.backend.exception.BadRequestException;
import com.apex.backend.exception.NotFoundException;
import com.apex.backend.model.Watchlist;
import com.apex.backend.repository.TradingStrategyRepository;
import com.apex.backend.repository.WatchlistItemRepository;
import com.apex.backend.repository.WatchlistRepository;
import com.apex.backend.repository.WatchlistStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WatchlistServiceTest {

    private WatchlistRepository watchlistRepository;
    private WatchlistItemRepository watchlistItemRepository;
    private WatchlistStockRepository watchlistStockRepository;
    private TradingStrategyRepository tradingStrategyRepository;
    private WatchlistService watchlistService;
    private Watchlist watchlist;

    @BeforeEach
    void setUp() {
        watchlistRepository = mock(WatchlistRepository.class);
        watchlistItemRepository = mock(WatchlistItemRepository.class);
        watchlistStockRepository = mock(WatchlistStockRepository.class);
        tradingStrategyRepository = mock(TradingStrategyRepository.class);
        watchlistService = new WatchlistService(
                watchlistRepository,
                watchlistItemRepository,
                watchlistStockRepository,
                tradingStrategyRepository
        );
        watchlist = Watchlist.builder()
                .id(1L)
                .userId(42L)
                .name("Default")
                .isDefault(true)
                .build();
        when(watchlistRepository.findByUserIdAndIsDefaultTrue(42L)).thenReturn(Optional.of(watchlist));
    }

    @Test
    void addSymbolsRejectsOverLimit() {
        when(watchlistItemRepository.countByWatchlistIdAndStatus(1L, com.apex.backend.model.WatchlistItem.Status.ACTIVE))
                .thenReturn(99L);
        assertThatThrownBy(() -> watchlistService.addSymbols(42L, List.of("NSE:AAA", "NSE:BBB")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at most 100");
    }

    @Test
    void replaceSymbolsRejectsOverLimit() {
        List<String> symbols = java.util.stream.IntStream.rangeClosed(1, 101)
                .mapToObj(i -> "NSE:SYM" + i)
                .toList();
        assertThatThrownBy(() -> watchlistService.replaceSymbols(42L, symbols))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at most 100");
    }

    @Test
    void removeSymbolThrowsWhenMissing() {
        when(watchlistItemRepository.findByWatchlistIdAndSymbol(1L, "NSE:ABC"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> watchlistService.removeSymbol(42L, "NSE:ABC"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Watchlist symbol not found");
    }

    @Test
    void resolveSymbolsForStrategyReturnsActiveSymbols() {
        when(watchlistStockRepository.findActiveSymbolsByStrategyId(7L))
                .thenReturn(List.of("NSE:ABC", "NSE:ABC", "NSE:XYZ"));

        List<String> resolved = watchlistService.resolveSymbolsForStrategy(7L);

        assertThat(resolved).containsExactlyInAnyOrder("NSE:ABC", "NSE:XYZ");
    }

    @Test
    void resolveSymbolsForStrategyOrDefaultFallsBackToUserWatchlist() {
        when(watchlistStockRepository.findActiveSymbolsByStrategyId(7L))
                .thenReturn(List.of());
        when(watchlistItemRepository.findByWatchlistIdAndStatusOrderByCreatedAtAsc(1L, com.apex.backend.model.WatchlistItem.Status.ACTIVE))
                .thenReturn(List.of(
                        com.apex.backend.model.WatchlistItem.builder().symbol("nse:aaa").build(),
                        com.apex.backend.model.WatchlistItem.builder().symbol("NSE:BBB").build()
                ));

        List<String> resolved = watchlistService.resolveSymbolsForStrategyOrDefault(42L, 7L);

        assertThat(resolved).containsExactly("NSE:AAA", "NSE:BBB");
    }

    @Test
    void resolveSymbolsForStrategyOrDefaultRejectsOverLimit() {
        List<String> symbols = java.util.stream.IntStream.rangeClosed(1, WatchlistService.MAX_SYMBOLS + 1)
                .mapToObj(i -> "NSE:SYM" + i)
                .toList();
        when(watchlistStockRepository.findActiveSymbolsByStrategyId(7L))
                .thenReturn(symbols);

        assertThatThrownBy(() -> watchlistService.resolveSymbolsForStrategyOrDefault(42L, 7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Symbols cannot exceed");
    }
}
