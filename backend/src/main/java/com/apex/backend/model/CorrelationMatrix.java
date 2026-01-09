package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "correlation_matrix")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrelationMatrix {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date")
    private LocalDate date;

    @Column(name = "symbol_a")
    private String symbolA;

    @Column(name = "symbol_b")
    private String symbolB;

    @Column(name = "correlation")
    private Double correlation;
}
