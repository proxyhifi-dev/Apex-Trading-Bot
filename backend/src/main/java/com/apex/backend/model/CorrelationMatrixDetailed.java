package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "correlation_matrix_detailed")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrelationMatrixDetailed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "symbol_a", nullable = false, length = 50)
    private String symbolA;

    @Column(name = "symbol_b", nullable = false, length = 50)
    private String symbolB;

    @Column(name = "correlation", nullable = false)
    private Double correlation;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
}
