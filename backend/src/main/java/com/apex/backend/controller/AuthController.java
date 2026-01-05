package com.apex.backend.controller;

import com.apex.backend.dto.UserProfileDTO;
import com.apex.backend.model.User;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Authentication Controller
 * Handles user login, registration, and JWT token management
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * User registration endpoint
     * Creates a new user with hashed password
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            log.info("User registration attempt for username: {}", request.getUsername());
            
            // Validate input
            if (request.getUsername() == null || request.getUsername().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Username cannot be empty"));
            }
            
            if (request.getPassword() == null || request.getPassword().length() < 6) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Password must be at least 6 characters"));
            }
            
            // Check if username already exists
            if (userRepository.existsByUsername(request.getUsername())) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Username already exists"));
            }
            
            // Check if email already exists
            if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Email already exists"));
            }
            
            // Create new user
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
            log.info("User registered successfully: {}", user.getUsername());
            
            // Generate tokens
            String accessToken = jwtTokenProvider.generateToken(user.getUsername(), user.getId(), user.getRole());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername(), user.getId());
            
            UserProfileDTO userProfile = UserProfileDTO.builder()
                .name(user.getUsername())
                .availableFunds(user.getAvailableFunds())
                .totalInvested(user.getTotalInvested())
                .currentValue(user.getCurrentValue())
                .todaysPnl(0.0)
                .build();
            
            return ResponseEntity.ok(new LoginResponse(userProfile, accessToken, refreshToken));
            
        } catch (Exception e) {
            log.error("Registration failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Registration failed: " + e.getMessage()));
        }
    }

    /**
     * User login endpoint
     * Authenticates user and returns JWT tokens
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            log.info("User login attempt for username: {}", request.getUsername());
            
            // Validate input
            if (request.getUsername() == null || request.getUsername().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Username cannot be empty"));
            }
            
            if (request.getPassword() == null || request.getPassword().isEmpty()) {
                log.warn("Login failed: empty password");
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Password cannot be empty"));
            }
            
            // Find user by username
            Optional<User> userOptional = userRepository.findByUsername(request.getUsername());
            
            if (userOptional.isEmpty()) {
                log.warn("Login failed: user not found - {}", request.getUsername());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid username or password"));
            }
            
            User user = userOptional.get();
            
            // Verify password
            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                log.warn("Login failed: invalid password for user - {}", request.getUsername());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid username or password"));
            }
            
            // Check if account is enabled
            if (!user.getEnabled()) {
                log.warn("Login failed: account disabled - {}", request.getUsername());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Account is disabled"));
            }
            
            // Update last login time
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);
            
            // Generate JWT tokens
            String accessToken = jwtTokenProvider.generateToken(user.getUsername(), user.getId(), user.getRole());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername(), user.getId());
            
            UserProfileDTO userProfile = UserProfileDTO.builder()
                .name(user.getUsername())
                .availableFunds(user.getAvailableFunds())
                .totalInvested(user.getTotalInvested())
                .currentValue(user.getCurrentValue())
                .todaysPnl(0.0) // TODO: Calculate from trades
                .build();
            
            log.info("User login successful: {}", user.getUsername());
            return ResponseEntity.ok(new LoginResponse(userProfile, accessToken, refreshToken));
            
        } catch (Exception e) {
            log.error("Login failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Login failed: " + e.getMessage()));
        }
    }

    /**
     * Get current user profile
     * Requires valid JWT token in Authorization header
     */
    @GetMapping("/user")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing token"));
            }
            
            String token = authHeader.substring(7);
            
            if (!jwtTokenProvider.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or expired token"));
            }
            
            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            Optional<User> userOptional = userRepository.findById(userId);
            
            if (userOptional.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("User not found"));
            }
            
            User user = userOptional.get();
            UserProfileDTO userProfile = UserProfileDTO.builder()
                .name(user.getUsername())
                .availableFunds(user.getAvailableFunds())
                .totalInvested(user.getTotalInvested())
                .currentValue(user.getCurrentValue())
                .todaysPnl(0.0) // TODO: Calculate from trades
                .build();
            
            return ResponseEntity.ok(userProfile);
        } catch (Exception e) {
            log.error("Failed to fetch user profile", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to fetch user profile"));
        }
    }

    /**
     * Refresh access token using refresh token
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest request) {
        try {
            String refreshToken = request.getRefreshToken();
            
            if (refreshToken == null || !jwtTokenProvider.validateToken(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or expired refresh token"));
            }
            
            String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
            Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
            
            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("User not found"));
            }
            
            User user = userOptional.get();
            String newAccessToken = jwtTokenProvider.generateToken(user.getUsername(), user.getId(), user.getRole());
            
            return ResponseEntity.ok(new RefreshTokenResponse(newAccessToken));
            
        } catch (Exception e) {
            log.error("Token refresh failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Token refresh failed"));
        }
    }

    /**
     * Logout endpoint
     * Client should delete token from storage
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        log.info("User logout");
        return ResponseEntity.ok(new MessageResponse("Logout successful"));
    }

    // ==================== DTOs ====================

    public static class RegisterRequest {
        private String username;
        private String password;
        private String email;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class LoginResponse {
        public UserProfileDTO user;
        public String accessToken;
        public String refreshToken;
        public long expiresIn = 3600;

        public LoginResponse(UserProfileDTO user, String accessToken, String refreshToken) {
            this.user = user;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }

    public static class RefreshTokenRequest {
        private String refreshToken;

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }

    public static class RefreshTokenResponse {
        public String accessToken;
        public long expiresIn = 3600;

        public RefreshTokenResponse(String accessToken) {
            this.accessToken = accessToken;
        }
    }

    public static class ErrorResponse {
        public String error;
        public long timestamp;

        public ErrorResponse(String error) {
            this.error = error;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static class MessageResponse {
        public String message;
        public long timestamp;

        public MessageResponse(String message) {
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
