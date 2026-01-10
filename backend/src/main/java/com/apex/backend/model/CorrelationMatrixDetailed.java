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

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String symbolA;

    @Column(nullable = false)
    private String symbolB;

    @Column(nullable = false)
    private Double correlation;

    @Column(nullable = false)
    private LocalDateTime calculatedAt;
}
