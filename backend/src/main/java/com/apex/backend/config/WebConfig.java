package com.apex.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final SecurityProperties securityProperties;
    private final Environment environment;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        CorsRegistry cors = registry.addMapping("/**")
                .allowedMethods(securityProperties.getCors().getAllowedMethods().toArray(new String[0]))
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        if (isProd()) {
            List<String> origins = securityProperties.getCors().getAllowedOrigins();
            cors.allowedOrigins(origins.toArray(new String[0]));
        } else {
            cors.allowedOriginPatterns("*");
        }
    }

    private boolean isProd() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
