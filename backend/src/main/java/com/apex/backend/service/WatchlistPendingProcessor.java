package com.apex.backend.service;

import com.apex.backend.model.WatchlistItem;
import com.apex.backend.repository.WatchlistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WatchlistPendingProcessor {

    private final WatchlistItemRepository watchlistItemRepository;
    private final ScheduledTaskGuard scheduledTaskGuard;

    @Scheduled(fixedDelayString = "${apex.watchlist.pending-interval-ms:120000}")
    @Transactional
    public void scheduledProcessPendingItems() {
        scheduledTaskGuard.run("watchlistPendingProcessor", this::processPendingItems);
    }

    @Transactional
    public int processPendingItems() {
        List<WatchlistItem> pendingItems = watchlistItemRepository.findByStatus(WatchlistItem.Status.PENDING);
        if (pendingItems.isEmpty()) {
            return 0;
        }
        for (WatchlistItem item : pendingItems) {
            if (item.getSymbol() == null || item.getSymbol().isBlank()) {
                item.setStatus(WatchlistItem.Status.FAILED);
                item.setFailureReason("Invalid symbol");
                log.warn("Watchlist item failed due to missing symbol: id={}", item.getId());
            } else {
                item.setStatus(WatchlistItem.Status.ACTIVE);
                item.setFailureReason(null);
                log.info("Watchlist item activated: id={} symbol={}", item.getId(), item.getSymbol());
            }
        }
        watchlistItemRepository.saveAll(pendingItems);
        return pendingItems.size();
    }
}
