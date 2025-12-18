package com.apex.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "fyers")
public class FyersConfig {
    private String appId;
    private String secretId;
    private String userId;
    private String accessToken;
}