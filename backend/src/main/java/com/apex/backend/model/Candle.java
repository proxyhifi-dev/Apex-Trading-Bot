package com.apex.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Candle {
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
    private LocalDateTime timestamp;
}