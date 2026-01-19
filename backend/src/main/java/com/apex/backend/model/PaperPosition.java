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

    @Column(name = "qty", nullable = false)
    private Integer quantity;

    @Column(name = "avg_price", nullable = false, precision = 19, scale = 4)
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

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
