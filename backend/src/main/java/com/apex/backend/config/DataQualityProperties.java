package com.apex.backend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "data-quality")
@Data
@Validated
public class DataQualityProperties {

    @Min(0)
    private int maxMissingCandles = 1;

    @Positive
    private double maxGapMultiplier = 2.0;

    @Positive
    private double outlierPct = 0.1;
}
