package com.apex.backend.controller;

import com.apex.backend.exception.BadRequestException;
import com.apex.backend.exception.NotFoundException;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.model.User;
import com.apex.backend.security.JwtTokenProvider;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.WatchlistService;
import com.apex.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "apex.dev.endpoints", havingValue = "true")
public class DevController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final WatchlistService watchlistService;

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
                                                             @RequestParam(defaultValue = "100") int count) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        if (count <= 0 || count > WatchlistService.MAX_SYMBOLS) {
            throw new BadRequestException("count must be between 1 and " + WatchlistService.MAX_SYMBOLS);
        }
        List<String> symbols = IntStream.rangeClosed(1, count)
                .mapToObj(i -> String.format("NSE:DEV%03d", i))
                .toList();
        watchlistService.replaceSymbols(principal.getUserId(), symbols);
        return ResponseEntity.ok(Map.of(
                "count", symbols.size(),
                "symbolsSeeded", symbols.size()
        ));
    }

    public record DevLoginRequest(Long userId, String role) {}
}
