package com.apex.backend.controller;

import com.apex.backend.dto.UserProfileDTO;
import com.apex.backend.model.User;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.security.JwtTokenProvider;
import com.apex.backend.service.FyersAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping(value = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
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
            if (!url.contains("client_id=") || url.matches(".*client_id=(&|$).*")) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Generated FYERS authUrl is missing client_id. Check fyers.api.app-id config."));
            }
            return ResponseEntity.ok(Map.of("authUrl", url, "state", state));
        } catch (Exception e) {
            log.error("Failed to generate Fyers auth URL", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to generate auth URL: " + e.getMessage()));
        }
    }

    @PostMapping("/fyers/callback")
    public ResponseEntity<?> handleFyersCallback(@RequestBody Map<String, String> payload,
                                                 @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String authCode = payload.get("auth_code");
        String state = payload.get("state");
        return processFyersCallback(authCode, state, authHeader);
    }

    @GetMapping("/fyers/callback")
    public ResponseEntity<?> handleFyersCallbackQuery(
            @RequestParam(value = "auth_code", required = false) String authCode,
            @RequestParam(value = "state", required = false) String state,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return processFyersCallback(authCode, state, authHeader);
    }

    private ResponseEntity<?> processFyersCallback(String authCode, String state, String authHeader) {
        try {
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

            FyersAuthService.FyersProfile fyersProfile = null;
            try {
                fyersProfile = fyersAuthService.getUserProfile(fyersToken);
                log.info("✅ Fyers profile fetched for fy_id: {}", fyersProfile.getFyId());
            } catch (Exception e) {
                log.warn("Unable to fetch Fyers profile: {}", e.getMessage());
            }

            String fyersId = fyersProfile != null ? fyersProfile.getFyId() : null;
            String fyersUsername = fyersId != null && !fyersId.isBlank()
                    ? "fyers_" + fyersId
                    : "fyers_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            String fyersEmail = fyersProfile != null ? fyersProfile.getEmail() : null;
            User user = null;

            // CASE 1: User is already logged in - link Fyers to existing account
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String jwt = authHeader.substring(7);
                    Long userId = jwtTokenProvider.getUserIdFromToken(jwt);

                    if (userId != null) {
                        Optional<User> userOpt = userRepository.findById(userId);
                        if (userOpt.isPresent()) {
                            user = userOpt.get();
                            if (fyersId != null && !fyersId.isBlank()) {
                                user.setFyersId(fyersId);
                            }
                            if (fyersEmail != null && !fyersEmail.isBlank() && !userRepository.existsByEmail(fyersEmail)) {
                                user.setEmail(fyersEmail);
                            }
                            user.setFyersConnected(true);
                            userRepository.save(user);
                            fyersAuthService.storeFyersToken(userId, fyersToken);
                            log.info("✅ Fyers account linked successfully for user ID: {}", userId);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Invalid JWT token during callback: {}", e.getMessage());
                }
            }

            // CASE 2: User is NOT logged in - check if state contains user ID
            if (user == null && state != null && state.startsWith("user_")) {
                try {
                    String[] parts = state.split("_");
                    if (parts.length >= 2) {
                        Long userId = Long.parseLong(parts[1]);
                        Optional<User> userOpt = userRepository.findById(userId);
                        if (userOpt.isPresent()) {
                            user = userOpt.get();
                            if (fyersId != null && !fyersId.isBlank()) {
                                user.setFyersId(fyersId);
                            }
                            if (fyersEmail != null && !fyersEmail.isBlank() && !userRepository.existsByEmail(fyersEmail)) {
                                user.setEmail(fyersEmail);
                            }
                            user.setFyersConnected(true);
                            userRepository.save(user);
                            fyersAuthService.storeFyersToken(userId, fyersToken);
                            log.info("✅ Fyers account linked via state for user ID: {}", userId);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract user ID from state: {}", e.getMessage());
                }
            }

            // CASE 3: No existing user - create a new account with Fyers
            if (user == null) {
                // Check if a user with this Fyers-based username already exists
                Optional<User> existingUser = Optional.empty();
                if (fyersId != null && !fyersId.isBlank()) {
                    existingUser = userRepository.findByFyersId(fyersId);
                }
                if (existingUser.isEmpty() && fyersEmail != null && !fyersEmail.isBlank()) {
                    existingUser = userRepository.findByEmail(fyersEmail);
                }
                if (existingUser.isEmpty()) {
                    existingUser = userRepository.findByUsername(fyersUsername);
                }

                if (existingUser.isPresent()) {
                    user = existingUser.get();
                    if (fyersId != null && !fyersId.isBlank()) {
                        user.setFyersId(fyersId);
                    }
                    if (fyersEmail != null && !fyersEmail.isBlank() && !userRepository.existsByEmail(fyersEmail)) {
                        user.setEmail(fyersEmail);
                    }
                    user.setFyersConnected(true);
                    userRepository.save(user);
                    log.info("Found existing Fyers user: {}", fyersUsername);
                } else {
                    String uniqueUsername = fyersUsername;
                    if (userRepository.existsByUsername(uniqueUsername)) {
                        uniqueUsername = fyersUsername + "_" + System.currentTimeMillis();
                    }
                    // Create new user account
                    user = User.builder()
                            .username(uniqueUsername)
                            .email(fyersEmail)
                            .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .role("USER")
                            .availableFunds(100000.0)
                            .totalInvested(0.0)
                            .currentValue(100000.0)
                            .enabled(true)
                            .createdAt(LocalDateTime.now())
                            .fyersId(fyersId)
                            .fyersConnected(true)
                            .build();

                    user = userRepository.save(user);
                    log.info("✅ Created new user account for Fyers: {}", uniqueUsername);
                }

                // Store Fyers token for the new/existing user
                fyersAuthService.storeFyersToken(user.getId(), fyersToken);
            }

            // Generate JWT tokens for frontend login
            String accessToken = jwtTokenProvider.generateToken(user.getUsername(), user.getId(), user.getRole());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername(), user.getId());

            UserProfileDTO userProfile = UserProfileDTO.builder()
                    .name(user.getUsername())
                    .availableFunds(user.getAvailableFunds())
                    .build();

            log.info("✅ Fyers authentication complete, returning JWT tokens for user: {}", user.getUsername());

            return ResponseEntity.ok(new LoginResponse(userProfile, accessToken, refreshToken));

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

    @GetMapping("/fyers/status")
    public ResponseEntity<?> getFyersStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.ok(new FyersStatusResponse(false, "Not authenticated"));
            }
            String jwt = authHeader.substring(7);
            Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
            if (userId == null) {
                return ResponseEntity.ok(new FyersStatusResponse(false, "Not authenticated"));
            }
            String token = fyersAuthService.getFyersToken(userId);
            if (token == null || token.isBlank()) {
                return ResponseEntity.ok(new FyersStatusResponse(false, "Not authenticated"));
            }
            return ResponseEntity.ok(new FyersStatusResponse(true, "Connected"));
        } catch (Exception e) {
            log.warn("Failed to resolve Fyers status", e);
            return ResponseEntity.ok(new FyersStatusResponse(false, "Not authenticated"));
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

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshRequest request) {
        try {
            if (request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Refresh token is required"));
            }

            String refreshToken = request.getRefreshToken();
            if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid refresh token"));
            }

            Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("User not found"));
            }

            User user = userOptional.get();
            String accessToken = jwtTokenProvider.generateToken(user.getUsername(), user.getId(), user.getRole());
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername(), user.getId());

            return ResponseEntity.ok(new RefreshResponse(accessToken, newRefreshToken));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Token refresh failed"));
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
    public static class RefreshRequest {
        private String refreshToken;
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }
    public static class RefreshResponse {
        public String accessToken;
        public String refreshToken;
        public RefreshResponse(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }
    public static class ErrorResponse {
        public String error;
        public ErrorResponse(String e) { error = e; }
    }
    public static class MessageResponse {
        public String message;
        public MessageResponse(String m) { message = m; }
    }
    public static class FyersStatusResponse {
        public boolean connected;
        public String reason;
        public FyersStatusResponse(boolean connected, String reason) {
            this.connected = connected;
            this.reason = reason;
        }
    }
}
