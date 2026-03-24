package com.smartwallet.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "objectifs")
@Data
@NoArgsConstructor
public class Objectif implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal montantCible;

    @Column(precision = 19, scale = 2)
    private BigDecimal montantActuel = BigDecimal.ZERO;

    private LocalDate dateLimite;

    private boolean atteint = false;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}
