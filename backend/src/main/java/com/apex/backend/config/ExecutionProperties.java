package com.apex.backend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "execution")
@Data
@Validated
public class ExecutionProperties {

    @Positive
    private double spreadPct = 0.0005;

    @Positive
    private double slippageAtrPct = 0.1;

    @Positive
    private double impactFactor = 0.05;

    @Positive
    private double avgDailyNotional = 10000000.0;

    @Min(0)
    private long latencyMillis = 200;

    @Positive
    private double latencyMovePctPerSecond = 0.0005;

    @Positive
    private double limitFillMaxDistancePct = 0.002;

    @Positive
    private double limitFillVolumeFactor = 0.2;

    private String defaultOrderType = "MARKET";
}
