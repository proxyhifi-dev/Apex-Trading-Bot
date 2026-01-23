package com.apex.backend.service;

import com.apex.backend.exception.BadRequestException;
import com.apex.backend.exception.NotFoundException;
import com.apex.backend.model.Watchlist;
import com.apex.backend.model.WatchlistItem;
import com.apex.backend.repository.WatchlistItemRepository;
import com.apex.backend.repository.WatchlistRepository;
import com.apex.backend.repository.WatchlistStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    public static final int MAX_SYMBOLS = 100;
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Z0-9:._-]{1,32}$");

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository watchlistItemRepository;

    // ✅ NEW: strategy-scoped watchlist table (watchlist_stocks)
    private final WatchlistStockRepository watchlistStockRepository;

    public Watchlist getDefaultWatchlist(Long userId) {
        return watchlistRepository.findByUserIdAndIsDefaultTrue(userId)
                .orElseGet(() -> watchlistRepository.save(Watchlist.builder()
                        .userId(userId)
                        .name("Default")
                        .isDefault(true)
                        .build()));
    }

    public Watchlist loadDefaultWithItems(Long userId) {
        Watchlist watchlist = getDefaultWatchlist(userId);
        List<WatchlistItem> items = watchlistItemRepository.findByWatchlistIdOrderByCreatedAtAsc(watchlist.getId());
        watchlist.setItems(items);
        return watchlist;
    }

    public Watchlist addSymbols(Long userId, List<String> symbols) {
        Watchlist watchlist = getDefaultWatchlist(userId);
        List<String> normalized = normalizeSymbols(symbols);
        if (normalized.isEmpty()) {
            throw new BadRequestException("Symbols are required");
        }
        long existingCount = watchlistItemRepository.countByWatchlistId(watchlist.getId());
        if (existingCount + normalized.size() > MAX_SYMBOLS) {
            throw new BadRequestException("Watchlist can contain at most " + MAX_SYMBOLS + " symbols");
        }
        for (String symbol : normalized) {
            watchlistItemRepository.findByWatchlistIdAndSymbol(watchlist.getId(), symbol)
                    .orElseGet(() -> watchlistItemRepository.save(WatchlistItem.builder()
                            .watchlist(watchlist)
                            .symbol(symbol)
                            .build()));
        }
        return loadDefaultWithItems(userId);
    }

    public Watchlist replaceSymbols(Long userId, List<String> symbols) {
        Watchlist watchlist = getDefaultWatchlist(userId);
        List<String> normalized = normalizeSymbols(symbols);
        if (normalized.size() > MAX_SYMBOLS) {
            throw new BadRequestException("Watchlist can contain at most " + MAX_SYMBOLS + " symbols");
        }
        List<WatchlistItem> existing = watchlistItemRepository.findByWatchlistIdOrderByCreatedAtAsc(watchlist.getId());
        watchlistItemRepository.deleteAll(existing);
        normalized.forEach(symbol -> watchlistItemRepository.save(WatchlistItem.builder()
                .watchlist(watchlist)
                .symbol(symbol)
                .build()));
        return loadDefaultWithItems(userId);
    }

    public void removeSymbol(Long userId, String symbol) {
        Watchlist watchlist = getDefaultWatchlist(userId);
        String normalized = normalizeSymbol(symbol);
        WatchlistItem item = watchlistItemRepository.findByWatchlistIdAndSymbol(watchlist.getId(), normalized)
                .orElseThrow(() -> new NotFoundException("Watchlist symbol not found"));
        watchlistItemRepository.delete(item);
    }

    /**
     * ✅ EXISTING (user default watchlist via watchlist_items)
     * Keep this for UI watchlist management.
     */
    public List<String> resolveSymbolsForUser(Long userId) {
        Watchlist watchlist = getDefaultWatchlist(userId);
        return watchlistItemRepository.findByWatchlistIdOrderByCreatedAtAsc(watchlist.getId()).stream()
                .map(WatchlistItem::getSymbol)
                .distinct()
                .toList();
    }

    /**
     * ✅ NEW (scanner WATCHLIST universe)
     * Uses strategy-scoped table: watchlist_stocks (active symbols for a strategy).
     */
    public List<String> resolveSymbolsForStrategy(Long strategyId) {
        if (strategyId == null) {
            throw new BadRequestException("strategyId is required for WATCHLIST universe");
        }
        return watchlistStockRepository.findActiveSymbolsByStrategyId(strategyId).stream()
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();
    }

    public boolean isWatchlistEmpty(Long userId) {
        Watchlist watchlist = getDefaultWatchlist(userId);
        return watchlistItemRepository.countByWatchlistId(watchlist.getId()) == 0;
    }

    private List<String> normalizeSymbols(List<String> symbols) {
        if (symbols == null) {
            return List.of();
        }
        return symbols.stream()
                .map(this::normalizeSymbol)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            throw new BadRequestException("Symbol is required");
        }
        String trimmed = symbol.trim().toUpperCase(Locale.ROOT);
        if (trimmed.isBlank()) {
            throw new BadRequestException("Symbol is required");
        }
        if (!SYMBOL_PATTERN.matcher(trimmed).matches()) {
            throw new BadRequestException("Invalid symbol format: " + symbol);
        }
        return trimmed;
    }
}
