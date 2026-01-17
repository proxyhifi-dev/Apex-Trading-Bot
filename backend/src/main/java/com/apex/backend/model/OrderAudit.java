package com.apex.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "order_audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String status;

    private String brokerOrderId;

    private String paperOrderId;

    @Column(length = 4000)
    private String requestPayload;

    @Column(length = 4000)
    private String responsePayload;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(length = 100)
    private String correlationId;
}
