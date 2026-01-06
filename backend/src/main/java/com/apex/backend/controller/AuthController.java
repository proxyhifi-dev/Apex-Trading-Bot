package com.apex.backend.controller;

import com.apex.backend.dto.UserProfileDTO;
import com.apex.backend.model.User;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.security.JwtTokenProvider;
import com.apex.backend.service.FyersAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FyersAuthService fyersAuthService;
    
    // Temporary storage for Fyers tokens before user links account
    private static final Map<String, String> tempFyersTokens = new ConcurrentHashMap<>();

    // ==================== Fyers Integration Endpoints ====================

    @GetMapping("/fyers/auth-url")
    public ResponseEntity<?> getFyersAuthUrl(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String state = "apex_" + System.currentTimeMillis();
            
            // If user is logged in, encode user ID in state for direct linking
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String jwt = authHeader.substring(7);
                    Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
                    state = "user_" + userId + "_" + System.currentTimeMillis();
                    log.info("Generating Fyers auth URL for logged-in user: {}", userId);
                } catch (Exception e) {
                    log.warn("Invalid JWT token, generating anonymous auth URL");
                }
            }
            
            String url = fyersAuthService.generateAuthUrl(state);
            log.info("Generated Fyers Auth URL with state: {}", state);
            return ResponseEntity.ok(Map.of("authUrl", url));
        } catch (Exception e) {
            log.error("Failed to generate Fyers auth URL", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to generate auth URL: " + e.getMessage()));
        }
    }

    @PostMapping("/fyers/callback")
    public ResponseEntity<?> handleFyersCallback(@RequestBody Map<String, String> payload,
                                                 @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String authCode = payload.get("auth_code");
            String state = payload.get("state");
            
            if (authCode == null) {
                log.error("Missing auth_code in callback payload");
                return ResponseEntity.badRequest().body(new ErrorResponse("Missing auth_code"));
            }

            log.info("Processing Fyers callback with state: {}", state);

            // Exchange Auth Code for Access Token
            String fyersToken;
            try {
                fyersToken = fyersAuthService.exchangeAuthCodeForToken(authCode);
                log.info("✅ Successfully obtained Fyers token");
            } catch (Exception e) {
                log.error("Failed to exchange auth code: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ErrorResponse("Failed to exchange auth code: " + e.getMessage()));
            }

            // CASE 1: User is already logged in - link immediately
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String jwt = authHeader.substring(7);
                    Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
                    
                    if (userId != null) {
                        fyersAuthService.storeFyersToken(userId, fyersToken);
                        log.info("✅ Fyers account linked successfully for user ID: {}", userId);
                        return ResponseEntity.ok(new MessageResponse("Fyers connected successfully"));
                    }
                } catch (Exception e) {
                    log.warn("Invalid JWT token during callback: {}", e.getMessage());
                    // Continue to CASE 2
                }
            }

            // CASE 2: User is NOT logged in - check if state contains user ID
            if (state != null && state.startsWith("user_")) {
                try {
                    String[] parts = state.split("_");
                    if (parts.length >= 2) {
                        Long userId = Long.parseLong(parts[1]);
                        fyersAuthService.storeFyersToken(userId, fyersToken);
                        log.info("✅ Fyers account linked via state for user ID: {}", userId);
                        return ResponseEntity.ok(new MessageResponse("Fyers connected successfully"));
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract user ID from state: {}", e.getMessage());
                    // Continue to CASE 3
                }
            }

            // CASE 3: User is NOT logged in and no user ID in state - store temporarily
            String tempKey = "temp_" + System.currentTimeMillis();
            tempFyersTokens.put(tempKey, fyersToken);
            log.info("⏳ Stored Fyers token temporarily with key: {}", tempKey);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Fyers authentication successful. Please login to link your account.");
            response.put("tempKey", tempKey);
            response.put("requiresLogin", "true");
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fyers callback failed with exception", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Fyers connection failed: " + e.getMessage()));
        }
    }
    
    @PostMapping("/fyers/link-temp-token")
    public ResponseEntity<?> linkTempFyersToken(
            @RequestBody Map<String, String> payload,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String tempKey = payload.get("tempKey");
            if (tempKey == null || !tempFyersTokens.containsKey(tempKey)) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Invalid or expired temp key"));
            }
            
            String jwt = authHeader.substring(7);
            Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
            
            String fyersToken = tempFyersTokens.remove(tempKey);
            fyersAuthService.storeFyersToken(userId, fyersToken);
            
            log.info("✅ Linked temporary Fyers token to user ID: {}", userId);
            return ResponseEntity.ok(new MessageResponse("Fyers account linked successfully"));
            
        } catch (Exception e) {
            log.error("Failed to link temp token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to link account: " + e.getMessage()));
        }
    }

    // ==================== Standard Auth Endpoints ====================

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            if (userRepository.existsByUsername(request.getUsername())) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Username already exists"));
            }

            User user = User.builder()
                    .username(request.getUsername())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .email(request.getEmail())
                    .role("USER")
                    .availableFunds(100000.0)
                    .totalInvested(0.0)
                    .currentValue(100000.0)
                    .enabled(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            user = userRepository.save(user);

            String accessToken = jwtTokenProvider.generateToken(user.getUsername(), user.getId(), user.getRole());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername(), user.getId());

            UserProfileDTO userProfile = UserProfileDTO.builder()
                    .name(user.getUsername())
                    .availableFunds(user.getAvailableFunds())
                    .build();

            return ResponseEntity.ok(new LoginResponse(userProfile, accessToken, refreshToken));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Registration failed: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            Optional<User> userOptional = userRepository.findByUsername(request.getUsername());

            if (userOptional.isEmpty() || !passwordEncoder.matches(request.getPassword(), userOptional.get().getPasswordHash())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid username or password"));
            }

            User user = userOptional.get();
            String accessToken = jwtTokenProvider.generateToken(user.getUsername(), user.getId(), user.getRole());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername(), user.getId());

            UserProfileDTO userProfile = UserProfileDTO.builder()
                    .name(user.getUsername())
                    .availableFunds(user.getAvailableFunds())
                    .build();

            return ResponseEntity.ok(new LoginResponse(userProfile, accessToken, refreshToken));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Login failed"));
        }
    }

    // DTO Classes
    public static class RegisterRequest {
        private String username; private String password; private String email;
        public String getUsername() { return username; } public void setUsername(String u) { username = u; }
        public String getPassword() { return password; } public void setPassword(String p) { password = p; }
        public String getEmail() { return email; } public void setEmail(String e) { email = e; }
    }
    public static class LoginRequest {
        private String username; private String password;
        public String getUsername() { return username; } public void setUsername(String u) { username = u; }
        public String getPassword() { return password; } public void setPassword(String p) { password = p; }
    }
    public static class LoginResponse {
        public UserProfileDTO user; public String accessToken; public String refreshToken;
        public LoginResponse(UserProfileDTO u, String a, String r) { user = u; accessToken = a; refreshToken = r; }
    }
    public static class ErrorResponse {
        public String error;
        public ErrorResponse(String e) { error = e; }
    }
    public static class MessageResponse {
        public String message;
        public MessageResponse(String m) { message = m; }
    }
}