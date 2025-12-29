package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "failed_operations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String operationType;

    @Column(nullable = false)
    private String details;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private boolean resolved;

    private int retryCount;

    @Column(nullable = false)
    private LocalDateTime timestamp;
}