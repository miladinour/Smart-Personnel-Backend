package com.smartwallet.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "budgets")
@Data
@NoArgsConstructor
public class Budget implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate dateDebut;
    private LocalDate dateFin;
    @Column(nullable = false, precision = 19, scale = 2)
    private java.math.BigDecimal montantLimite;

    @Enumerated(EnumType.STRING)
    private BudgetType type; // GLOBAL or CATEGORIE

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id")
    private Categorie categorie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public void calculerReste(double montant, double limite) {
        // Implementation logic
    }

    public boolean verifierDepassement(double montant, double limite) {
        return montant > limite;
    }

    public enum BudgetType {
        GLOBAL, CATEGORIE
    }
}
