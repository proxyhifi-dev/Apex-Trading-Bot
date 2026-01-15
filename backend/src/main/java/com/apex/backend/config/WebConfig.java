package com.apex.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final SecurityProperties securityProperties;
    private final AllowedOriginResolver allowedOriginResolver;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOriginResolver.resolveCorsAllowedOrigins().toArray(new String[0]))
                .allowedMethods(securityProperties.getCors().getAllowedMethods().toArray(new String[0]))
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
