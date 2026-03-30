package com.smartwallet.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.LocalDate;
@Entity
@Getter
@Setter
@Table(name = "dettes")
public class Dette {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ami;

    @Column(nullable = false)
    private Double montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DetteType type; // PRETE (on me doit) ou EMPRUNTE (je dois)

    private String description;

    @Column(nullable = false)
    private Boolean isPaye = false;

    @Column(nullable = false)
    private Double montantPaye = 0.0;

    private LocalDate dateLimite;

    @CreationTimestamp
    private LocalDateTime date;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
}
