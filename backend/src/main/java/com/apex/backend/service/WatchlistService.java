package com.apex.backend.service;

import com.apex.backend.dto.WatchlistEntryRequest;
import com.apex.backend.exception.BadRequestException;
import com.apex.backend.exception.NotFoundException;
import com.apex.backend.model.WatchlistEntry;
import com.apex.backend.repository.WatchlistEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistEntryRepository watchlistEntryRepository;

    public List<WatchlistEntry> getWatchlist(Long userId) {
        return watchlistEntryRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public WatchlistEntry addEntry(Long userId, WatchlistEntryRequest request) {
        String symbol = normalize(request.getSymbol());
        String exchange = normalize(request.getExchange());
        if (symbol == null || exchange == null) {
            throw new BadRequestException("Symbol and exchange are required");
        }
        return watchlistEntryRepository
                .findByUserIdAndSymbolIgnoreCaseAndExchangeIgnoreCase(userId, symbol, exchange)
                .orElseGet(() -> watchlistEntryRepository.save(WatchlistEntry.builder()
                        .userId(userId)
                        .symbol(symbol)
                        .exchange(exchange)
                        .build()));
    }

    public void deleteEntry(Long userId, Long id) {
        WatchlistEntry entry = watchlistEntryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Watchlist entry not found"));
        if (!entry.getUserId().equals(userId)) {
            throw new NotFoundException("Watchlist entry not found");
        }
        watchlistEntryRepository.delete(entry);
    }

    public List<String> resolveSymbolsForUser(Long userId) {
        return getWatchlist(userId).stream()
                .map(this::toScannerSymbol)
                .distinct()
                .toList();
    }

    public boolean isWatchlistEmpty(Long userId) {
        return watchlistEntryRepository.findByUserIdOrderByCreatedAtDesc(userId).isEmpty();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String toScannerSymbol(WatchlistEntry entry) {
        String symbol = entry.getSymbol();
        if (symbol.contains(":")) {
            return symbol;
        }
        return entry.getExchange() + ":" + symbol;
    }
}
