package com.apex.backend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "risk")
@Data
@Validated
public class RiskProperties {

    private Ruin ruin = new Ruin();
    private Correlation correlation = new Correlation();

    @Data
    public static class Ruin {
        @Positive
        private double riskPerTradePct = 0.01;

        @Positive
        private double bankrollR = 50.0;
    }

    @Data
    public static class Correlation {
        @Min(2)
        private int lookback = 50;

        @Positive
        private double spikeThreshold = 0.75;

        @Positive
        private double sizingMultiplierOnSpike = 0.5;
    }
}
