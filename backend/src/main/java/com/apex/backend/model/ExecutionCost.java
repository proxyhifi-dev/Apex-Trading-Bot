package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "execution_costs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderId;

    @Column(nullable = false, unique = true)
    private String clientOrderId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private Integer quantity;

    @Column(precision = 19, scale = 4)
    private BigDecimal expectedCost;

    @Column(precision = 19, scale = 4)
    private BigDecimal realizedCost;

    @Column(precision = 19, scale = 4)
    private BigDecimal spreadCost;

    @Column(precision = 19, scale = 4)
    private BigDecimal slippageCost;

    @Column(precision = 19, scale = 4)
    private BigDecimal commissionCost;

    @Column(precision = 19, scale = 4)
    private BigDecimal taxCost;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
