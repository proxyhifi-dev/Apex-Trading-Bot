package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "paper_positions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String side;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal averagePrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal lastPrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal unrealizedPnl;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime entryTime;

    private LocalDateTime exitTime;
}
