package com.apex.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sector_mappings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectorMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String sector;
}