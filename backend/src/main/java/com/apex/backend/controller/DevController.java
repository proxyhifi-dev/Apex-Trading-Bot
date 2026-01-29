package com.apex.backend.controller;

import com.apex.backend.exception.BadRequestException;
import com.apex.backend.exception.NotFoundException;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.model.User;
import com.apex.backend.security.JwtTokenProvider;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.InstrumentCacheService;
import com.apex.backend.service.UniverseService;
import com.apex.backend.service.WatchlistService;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.repository.InstrumentRepository;
import com.apex.backend.model.Instrument;
import com.apex.backend.model.InstrumentDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Conditional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@Conditional(com.apex.backend.config.DevEndpointCondition.class)
public class DevController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final WatchlistService watchlistService;
    private final InstrumentCacheService instrumentCacheService;
    private final InstrumentRepository instrumentRepository;
    private final UniverseService universeService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody DevLoginRequest request) {
        if (request == null || request.userId() == null) {
            throw new BadRequestException("userId is required");
        }
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        String role = (request.role() != null && !request.role().isBlank()) ? request.role() : user.getRole();
        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getId(), role);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "userId", user.getId(),
                "username", user.getUsername(),
                "role", role
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return ResponseEntity.ok(Map.of(
                "userId", principal.getUserId(),
                "username", principal.getUsername(),
                "role", principal.getRole()
        ));
    }

    @PostMapping("/seed-watchlist")
    public ResponseEntity<Map<String, Object>> seedWatchlist(@AuthenticationPrincipal UserPrincipal principal,
                                                             @RequestParam(defaultValue = "100") int count,
                                                             @RequestParam(defaultValue = "NIFTY100") String universe) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        if (count <= 0 || count > WatchlistService.MAX_SYMBOLS) {
            throw new BadRequestException("count must be between 1 and " + WatchlistService.MAX_SYMBOLS);
        }
        List<String> symbols = universeService.loadUniverse(universe).stream()
                .limit(count)
                .toList();
        WatchlistService.SeedResult result = watchlistService.seedSymbols(principal.getUserId(), symbols);
        return ResponseEntity.ok(Map.of(
                "added", result.added(),
                "skipped", result.skipped(),
                "total", result.total()
        ));
    }

    @PostMapping("/seed-instruments")
    public ResponseEntity<Map<String, Object>> seedInstruments(@AuthenticationPrincipal UserPrincipal principal,
                                                               @RequestParam(defaultValue = "100") int count) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        if (count <= 0 || count > 1000) {
            throw new BadRequestException("count must be between 1 and 1000");
        }
        List<InstrumentDefinition> definitions = instrumentCacheService.listDefinitions(count);
        if (definitions.isEmpty()) {
            throw new BadRequestException("No instrument definitions available to seed");
        }

        List<Instrument> toInsert = definitions.stream()
                .filter(def -> def.getSymbol() != null && !def.getSymbol().isBlank())
                .filter(def -> instrumentRepository.findBySymbolIgnoreCase(def.getSymbol()).isEmpty())
                .map(def -> Instrument.builder()
                        .symbol(def.getSymbol())
                        .tradingSymbol(def.getSymbol())
                        .name(def.getName())
                        .exchange(def.getExchange())
                        .segment(def.getSegment())
                        .tickSize(def.getTickSize())
                        .lotSize(def.getLotSize())
                        .isin(def.getIsin())
                        .build())
                .collect(Collectors.toList());

        if (!toInsert.isEmpty()) {
            instrumentRepository.saveAll(toInsert);
        }
        return ResponseEntity.ok(Map.of(
                "seeded", !toInsert.isEmpty(),
                "requested", count,
                "inserted", toInsert.size(),
                "existing", instrumentRepository.count()
        ));
    }

    public record DevLoginRequest(Long userId, String role) {}
}
