package com.apex.backend.controller;

import com.apex.backend.dto.UserProfileDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller
 * Handles user login and authentication
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class AuthController {

    @Value("${apex.trading.capital:100000}")
    private double initialCapital;

    /**
     * User login endpoint
     * Accepts password-based authentication
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            log.info("User login attempt");
            
            // Simple password validation (in production, use proper authentication)
            if (request.getPassword() == null || request.getPassword().isEmpty()) {
                log.warn("Login failed: empty password");
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Password cannot be empty"));
            }
            
            // For now, accept any non-empty password (replace with real auth in production)
            UserProfileDTO user = UserProfileDTO.builder()
                .name("Trading User")
                .availableFunds(initialCapital)
                .totalInvested(0.0)
                .currentValue(initialCapital)
                .todaysPnl(0.0)
                .build();
            
            // Generate a mock JWT token (replace with real JWT generation)
            String token = generateMockToken(user);
            
            log.info("User login successful");
            return ResponseEntity.ok(new LoginResponse(user, token));
            
        } catch (Exception e) {
            log.error("Login failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Login failed: " + e.getMessage()));
        }
    }

    /**
     * Get current user profile
     */
    @GetMapping("/user")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String token) {
        try {
            log.info("Fetching current user profile");
            
            if (token == null || !token.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing token"));
            }
            
            UserProfileDTO user = UserProfileDTO.builder()
                .name("Trading User")
                .availableFunds(initialCapital)
                .totalInvested(0.0)
                .currentValue(initialCapital)
                .todaysPnl(0.0)
                .build();
            
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            log.error("Failed to fetch user profile", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to fetch user profile"));
        }
    }

    /**
     * Logout endpoint
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String token) {
        try {
            log.info("User logout");
            return ResponseEntity.ok(new MessageResponse("Logout successful"));
        } catch (Exception e) {
            log.error("Logout failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Logout failed"));
        }
    }

    /**
     * Generate mock JWT token
     * Replace with real JWT generation in production
     */
    private String generateMockToken(UserProfileDTO user) {
        return "Bearer_" + System.currentTimeMillis() + "_" + user.getName().hashCode();
    }

    // ==================== DTOs ====================

    public static class LoginRequest {
        public String password;

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class LoginResponse {
        public UserProfileDTO user;
        public String token;

        public LoginResponse(UserProfileDTO user, String token) {
            this.user = user;
            this.token = token;
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
