package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "paper_trades")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperTrade {

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
    private Double entryPrice;

    private Double exitPrice;

    @Column(nullable = false)
    private LocalDateTime entryTime;

    private LocalDateTime exitTime;

    private Double realizedPnl;

    @Column(nullable = false)
    private String status;
}
