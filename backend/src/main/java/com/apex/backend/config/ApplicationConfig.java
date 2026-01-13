package com.apex.backend.config;

import com.apex.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Application Configuration
 * Defines core beans for authentication and security
 */
@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    private final UserRepository userRepository;

    @Bean
    public UserDetailsService userDetailsService() {
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\bollu\\github\\Apex-Trading-Bot\\.cursor\\debug.log", true);
            fw.write("{\"id\":\"log_" + System.currentTimeMillis() + "_userDetails\",\"timestamp\":" + java.time.Instant.now().toEpochMilli() + ",\"location\":\"ApplicationConfig.java:25\",\"message\":\"Creating UserDetailsService bean\",\"data\":{\"userRepoPresent\":\"" + (userRepository != null) + "\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"D\"}\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        return username -> {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\bollu\\github\\Apex-Trading-Bot\\.cursor\\debug.log", true);
                fw.write("{\"id\":\"log_" + System.currentTimeMillis() + "_userLookup\",\"timestamp\":" + java.time.Instant.now().toEpochMilli() + ",\"location\":\"ApplicationConfig.java:27\",\"message\":\"Looking up user\",\"data\":{\"username\":\"" + (username != null ? username : "null") + "\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"D\"}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            return userRepository.findByUsername(username)
                    .map(user -> {
                        // #region agent log
                        try {
                            java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\bollu\\github\\Apex-Trading-Bot\\.cursor\\debug.log", true);
                            fw.write("{\"id\":\"log_" + System.currentTimeMillis() + "_userFound\",\"timestamp\":" + java.time.Instant.now().toEpochMilli() + ",\"location\":\"ApplicationConfig.java:30\",\"message\":\"User found in database\",\"data\":{\"userId\":\"" + user.getId() + "\",\"role\":\"" + (user.getRole() != null ? user.getRole() : "null") + "\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"D\"}\n");
                            fw.close();
                        } catch (Exception e) {}
                        // #endregion
                        return org.springframework.security.core.userdetails.User.builder()
                                .username(user.getUsername())
                                .password(user.getPasswordHash())
                                .roles(user.getRole())
                                .build();
                    })
                    .orElseThrow(() -> {
                        // #region agent log
                        try {
                            java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\bollu\\github\\Apex-Trading-Bot\\.cursor\\debug.log", true);
                            fw.write("{\"id\":\"log_" + System.currentTimeMillis() + "_userNotFound\",\"timestamp\":" + java.time.Instant.now().toEpochMilli() + ",\"location\":\"ApplicationConfig.java:36\",\"message\":\"User not found in database\",\"data\":{\"username\":\"" + (username != null ? username : "null") + "\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"D\"}\n");
                            fw.close();
                        } catch (Exception e) {}
                        // #endregion
                        return new UsernameNotFoundException("User not found");
                    });
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}