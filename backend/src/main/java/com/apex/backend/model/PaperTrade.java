package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class PaperTrade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String symbol;
    private int quantity;
    private double entryPrice;
    private String side; // BUY or SELL
    private LocalDateTime timestamp = LocalDateTime.now();
}
