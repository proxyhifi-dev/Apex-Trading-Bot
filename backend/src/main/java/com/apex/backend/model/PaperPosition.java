package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(nullable = false)
    private Double averagePrice;

    private Double lastPrice;

    private Double unrealizedPnl;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime entryTime;

    private LocalDateTime exitTime;
}
