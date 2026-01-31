package com.apex.backend.model;

import jakarta.persistence.*;
import com.apex.backend.security.TokenEncryptionConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.apex.backend.util.MoneyUtils;

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

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal availableFunds = MoneyUtils.bd(100000.0);

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalInvested = MoneyUtils.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal currentValue = MoneyUtils.bd(100000.0);

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

    @Column(length = 1500)
    @Convert(converter = TokenEncryptionConverter.class)
    private String fyersToken;

    @Column(length = 1500)
    @Convert(converter = TokenEncryptionConverter.class)
    private String fyersRefreshToken;

    @Column(nullable = false)
    @Builder.Default
    private Boolean fyersConnected = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean fyersTokenActive = true;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
