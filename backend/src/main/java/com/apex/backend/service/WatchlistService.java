package com.apex.backend.service;

import com.apex.backend.exception.BadRequestException;
import com.apex.backend.exception.NotFoundException;
import com.apex.backend.model.Watchlist;
import com.apex.backend.model.WatchlistItem;
import com.apex.backend.model.TradingStrategy;
import com.apex.backend.repository.TradingStrategyRepository;
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
    private final TradingStrategyRepository tradingStrategyRepository;

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
        List<WatchlistItem> items = watchlistItemRepository.findByWatchlistIdOrderByCreatedAtAsc(
                watchlist.getId()
        );
        watchlist.setItems(items);
        return watchlist;
    }

    public Watchlist addSymbols(Long userId, List<String> symbols) {
        Watchlist watchlist = getDefaultWatchlist(userId);
        List<String> normalized = normalizeSymbols(symbols);
        if (normalized.isEmpty()) {
            throw new BadRequestException("Symbols are required");
        }
        long existingCount = watchlistItemRepository.countByWatchlistIdAndStatus(watchlist.getId(), WatchlistItem.Status.ACTIVE);
        if (existingCount + normalized.size() > MAX_SYMBOLS) {
            throw new BadRequestException("Watchlist can contain at most " + MAX_SYMBOLS + " symbols");
        }
        for (String symbol : normalized) {
            watchlistItemRepository.findByWatchlistIdAndSymbol(watchlist.getId(), symbol)
                    .orElseGet(() -> watchlistItemRepository.save(WatchlistItem.builder()
                            .watchlist(watchlist)
                            .symbol(symbol)
                            .status(WatchlistItem.Status.ACTIVE)
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
                .status(WatchlistItem.Status.ACTIVE)
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
        return watchlistItemRepository.findByWatchlistIdAndStatusOrderByCreatedAtAsc(
                        watchlist.getId(),
                        WatchlistItem.Status.ACTIVE
                ).stream()
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
        List<String> symbols = normalizeSymbols(watchlistStockRepository.findActiveSymbolsByStrategyId(strategyId));
        enforceSymbolLimit(symbols);
        return symbols;
    }

    public List<String> resolveSymbolsForStrategyOrDefault(Long userId, Long strategyId) {
        List<String> strategySymbols = List.of();
        if (strategyId != null) {
            strategySymbols = normalizeSymbols(watchlistStockRepository.findActiveSymbolsByStrategyId(strategyId));
        }
        if (!strategySymbols.isEmpty()) {
            enforceSymbolLimit(strategySymbols);
            return strategySymbols;
        }
        List<String> fallback = normalizeSymbols(resolveSymbolsForUser(userId));
        enforceSymbolLimit(fallback);
        return fallback;
    }

    public Long resolveDefaultStrategyId() {
        List<TradingStrategy> strategies = tradingStrategyRepository.findByActiveTrue().stream()
                .sorted(Comparator.comparing(TradingStrategy::getId))
                .toList();
        if (strategies.isEmpty()) {
            return null;
        }
        for (TradingStrategy strategy : strategies) {
            List<String> symbols = watchlistStockRepository.findActiveSymbolsByStrategyId(strategy.getId());
            if (symbols != null && !symbols.isEmpty()) {
                return strategy.getId();
            }
        }
        return strategies.get(0).getId();
    }

    public List<String> resolveSymbolsForScanner(Long userId, Long strategyId) {
        return resolveSymbolsForStrategyOrDefault(userId, strategyId);
    }

    public boolean isWatchlistEmpty(Long userId) {
        Watchlist watchlist = getDefaultWatchlist(userId);
        boolean userWatchlistEmpty = watchlistItemRepository.countByWatchlistIdAndStatus(
                watchlist.getId(),
                WatchlistItem.Status.ACTIVE
        ) == 0;
        if (!userWatchlistEmpty) {
            return false;
        }
        Long defaultStrategyId = resolveDefaultStrategyId();
        if (defaultStrategyId == null) {
            return true;
        }
        return watchlistStockRepository.findActiveSymbolsByStrategyId(defaultStrategyId).isEmpty();
    }

    public boolean hasDefaultWatchlistItems(Long userId) {
        Watchlist watchlist = getDefaultWatchlist(userId);
        return watchlistItemRepository.countByWatchlistId(watchlist.getId()) > 0;
    }

    List<String> normalizeSymbols(List<String> symbols) {
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

    private void enforceSymbolLimit(List<String> symbols) {
        if (symbols.size() > MAX_SYMBOLS) {
            throw new BadRequestException("Symbols cannot exceed " + MAX_SYMBOLS);
        }
    }
}
