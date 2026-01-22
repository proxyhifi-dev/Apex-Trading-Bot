package com.apex.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "apex.security")
@Data
public class SecurityProperties {

    private Cors cors = new Cors();
    private Websocket websocket = new Websocket();
    private boolean publicHealthEndpoint = true;

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }

    @Data
    public static class Websocket {
        private List<String> allowedOrigins = new ArrayList<>();
    }
}
