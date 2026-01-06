package com.apex.backend.controller;

import com.apex.backend.dto.UserProfileDTO;
import com.apex.backend.model.User;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.security.JwtTokenProvider;
import com.apex.backend.service.FyersAuthService; // Import this
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FyersAuthService fyersAuthService; // Inject Fyers Service

    // ==================== Fyers Integration Endpoints ====================

    @GetMapping("/fyers/auth-url")
    public ResponseEntity<?> getFyersAuthUrl() {
        try {
            // Generate the login URL using the App ID from config
            String url = fyersAuthService.generateAuthUrl("apex_app_state");
            log.info("Generated Fyers Auth URL: {}", url);
            return ResponseEntity.ok(Map.of("authUrl", url));
        } catch (Exception e) {
            log.error("Failed to generate Fyers auth URL", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to generate auth URL: " + e.getMessage()));
        }
    }

    @PostMapping("/fyers/callback")
    public ResponseEntity<?> handleFyersCallback(@RequestBody Map<String, String> payload,
                                                 @RequestHeader("Authorization") String authHeader) {
        try {
            String authCode = payload.get("auth_code");
            if (authCode == null) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Missing auth_code"));
            }

            // Exchange Auth Code for Access Token
            String fyersToken = fyersAuthService.exchangeAuthCodeForToken(authCode);

            // Identify the current user
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("User not authenticated"));
            }
            String jwt = authHeader.substring(7);
            Long userId = jwtTokenProvider.getUserIdFromToken(jwt);

            // Store the token
            fyersAuthService.storeFyersToken(userId, fyersToken);

            log.info("Fyers connected successfully for user ID: {}", userId);
            return ResponseEntity.ok(new MessageResponse("Fyers connected successfully"));

        } catch (Exception e) {
            log.error("Fyers connection failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Fyers connection failed: " + e.getMessage()));
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

    // Keep your DTO classes at the bottom exactly as they were
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