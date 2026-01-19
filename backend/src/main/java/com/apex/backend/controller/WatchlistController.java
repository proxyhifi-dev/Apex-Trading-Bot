package com.apex.backend.controller;

import com.apex.backend.dto.WatchlistEntryRequest;
import com.apex.backend.dto.WatchlistEntryResponse;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.model.WatchlistEntry;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
@Tag(name = "Watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping
    @Operation(summary = "Get watchlist entries")
    public ResponseEntity<List<WatchlistEntryResponse>> getWatchlist(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        List<WatchlistEntryResponse> response = watchlistService.getWatchlist(userId).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Add a watchlist entry")
    public ResponseEntity<WatchlistEntryResponse> addEntry(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody WatchlistEntryRequest request) {
        Long userId = requireUserId(principal);
        WatchlistEntry entry = watchlistService.addEntry(userId, request);
        return ResponseEntity.ok(toResponse(entry));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a watchlist entry")
    public ResponseEntity<Void> deleteEntry(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id) {
        Long userId = requireUserId(principal);
        watchlistService.deleteEntry(userId, id);
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }

    private WatchlistEntryResponse toResponse(WatchlistEntry entry) {
        return WatchlistEntryResponse.builder()
                .id(entry.getId())
                .symbol(entry.getSymbol())
                .exchange(entry.getExchange())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
