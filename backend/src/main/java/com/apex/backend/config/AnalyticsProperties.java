package com.apex.backend.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "analytics")
@Data
@Validated
public class AnalyticsProperties {

    private Validation validation = new Validation();
    private Cvar cvar = new Cvar();

    @Data
    public static class Validation {
        @Min(1)
        private int bootstrapSamples = 500;

        @Min(1)
        private int numTrials = 50;
    }

    @Data
    public static class Cvar {
        @Min(50)
        @Max(99)
        private int confidence = 95;
    }
}
