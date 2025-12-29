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

    private LocalDate date;
    private String symbolA;
    private String symbolB;
    private Double correlation;
}