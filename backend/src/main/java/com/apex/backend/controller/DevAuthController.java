package com.apex.backend.controller;

import com.apex.backend.model.User;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Profile({"dev", "local"})
@RequiredArgsConstructor
public class DevAuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @PostMapping("/dev-token")
    public ResponseEntity<?> generateDevToken(@RequestBody(required = false) DevTokenRequest request) {
        Optional<User> user = resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found for dev token generation"));
        }
        User target = user.get();
        String token = jwtTokenProvider.generateToken(target.getUsername(), target.getId(), target.getRole());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "userId", target.getId(),
                "username", target.getUsername(),
                "role", target.getRole()
        ));
    }

    private Optional<User> resolveUser(DevTokenRequest request) {
        if (request == null) {
            return userRepository.findTopByOrderByIdAsc();
        }
        if (request.userId() != null) {
            return userRepository.findById(request.userId());
        }
        if (request.username() != null && !request.username().isBlank()) {
            return userRepository.findByUsername(request.username());
        }
        return userRepository.findTopByOrderByIdAsc();
    }

    public record DevTokenRequest(Long userId, String username) {}
}
