package com.smartwallet.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

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
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    // ✅ Cascade delete associated alerts when budget is deleted
    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Alerte> alertes;

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
