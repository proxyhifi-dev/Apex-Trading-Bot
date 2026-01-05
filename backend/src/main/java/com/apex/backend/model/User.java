package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * User Entity for authentication and profile management
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    @Builder.Default
    private String role = "USER";

    @Column(nullable = false)
    @Builder.Default
    private Double availableFunds = 100000.0;

    @Column(nullable = false)
    @Builder.Default
    private Double totalInvested = 0.0;

    @Column(nullable = false)
    @Builder.Default
    private Double currentValue = 100000.0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime lastLogin;

    // Fyers OAuth fields
    @Column(unique = true, length = 50)
    private String fyersId;

    @Column(length = 500)
    private String fyersToken;

    @Column(nullable = false)
    @Builder.Default
    private Boolean fyersConnected = false;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
