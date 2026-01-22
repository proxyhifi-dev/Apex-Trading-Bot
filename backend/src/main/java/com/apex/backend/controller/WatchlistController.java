package com.apex.backend.controller;

import com.apex.backend.dto.WatchlistItemsRequest;
import com.apex.backend.dto.WatchlistItemResponse;
import com.apex.backend.dto.WatchlistResponse;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.model.Watchlist;
import com.apex.backend.model.WatchlistItem;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import org.springframework.web.bind.annotation.PutMapping;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
@Tag(name = "Watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping
    @Operation(summary = "Get the default watchlist")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = WatchlistResponse.class)))
    public ResponseEntity<WatchlistResponse> getWatchlist(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        Watchlist watchlist = watchlistService.loadDefaultWithItems(userId);
        return ResponseEntity.ok(toResponse(watchlist));
    }

    @PostMapping("/items")
    @Operation(summary = "Add symbols to the default watchlist")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = WatchlistResponse.class)))
    public ResponseEntity<WatchlistResponse> addEntries(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody WatchlistItemsRequest request) {
        Long userId = requireUserId(principal);
        Watchlist watchlist = watchlistService.addSymbols(userId, request.getSymbols());
        return ResponseEntity.ok(toResponse(watchlist));
    }

    @PutMapping
    @Operation(summary = "Replace the default watchlist with the provided symbols")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = WatchlistResponse.class)))
    public ResponseEntity<WatchlistResponse> replaceWatchlist(@AuthenticationPrincipal UserPrincipal principal,
                                                              @Valid @RequestBody WatchlistItemsRequest request) {
        Long userId = requireUserId(principal);
        Watchlist watchlist = watchlistService.replaceSymbols(userId, request.getSymbols());
        return ResponseEntity.ok(toResponse(watchlist));
    }

    @DeleteMapping("/items/{symbol}")
    @Operation(summary = "Remove a symbol from the default watchlist")
    @ApiResponse(responseCode = "204", content = @Content)
    public ResponseEntity<Void> deleteEntry(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable String symbol) {
        Long userId = requireUserId(principal);
        watchlistService.removeSymbol(userId, symbol);
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }

    private WatchlistResponse toResponse(Watchlist watchlist) {
        List<WatchlistItemResponse> items = watchlist.getItems().stream()
                .map(this::toItemResponse)
                .toList();
        return WatchlistResponse.builder()
                .id(watchlist.getId())
                .name(watchlist.getName())
                .isDefault(watchlist.isDefault())
                .maxItems(WatchlistService.MAX_SYMBOLS)
                .itemCount(items.size())
                .createdAt(watchlist.getCreatedAt())
                .updatedAt(watchlist.getUpdatedAt())
                .items(items)
                .build();
    }

    private WatchlistItemResponse toItemResponse(WatchlistItem item) {
        return WatchlistItemResponse.builder()
                .symbol(item.getSymbol())
                .addedAt(item.getCreatedAt())
                .build();
    }
}
