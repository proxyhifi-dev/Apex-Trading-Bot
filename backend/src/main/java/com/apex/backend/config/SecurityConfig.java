package com.apex.backend.config;

import com.apex.backend.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\bollu\\github\\Apex-Trading-Bot\\.cursor\\debug.log", true);
            fw.write("{\"id\":\"log_" + System.currentTimeMillis() + "_securityConfig\",\"timestamp\":" + java.time.Instant.now().toEpochMilli() + ",\"location\":\"SecurityConfig.java:41\",\"message\":\"Creating SecurityFilterChain bean\",\"data\":{\"jwtFilterPresent\":\"" + (jwtAuthFilter != null) + "\",\"userDetailsPresent\":\"" + (userDetailsService != null) + "\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"D\"}\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 1. Enable CORS
            .authorizeHttpRequests(auth -> auth
                // 2. Allow these endpoints without login
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/favicon.ico").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // ✅ FIX: Allow WebSocket handshake without authentication
                .requestMatchers("/ws/**").permitAll()
                // All other endpoints need login
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // #region agent log
        try {
            java.io.FileWriter fw2 = new java.io.FileWriter("c:\\Users\\bollu\\github\\Apex-Trading-Bot\\.cursor\\debug.log", true);
            fw2.write("{\"id\":\"log_" + System.currentTimeMillis() + "_securityBuilt\",\"timestamp\":" + java.time.Instant.now().toEpochMilli() + ",\"location\":\"SecurityConfig.java:62\",\"message\":\"SecurityFilterChain built successfully\",\"data\":{},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"D\"}\n");
            fw2.close();
        } catch (Exception e) {}
        // #endregion
        return http.build();
    }

    // 3. Define CORS rules to allow 127.0.0.1
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow BOTH localhost and 127.0.0.1
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:4200",
            "http://127.0.0.1:4200"
        ));
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS"
        ));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
