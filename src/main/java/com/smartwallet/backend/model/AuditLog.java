package com.smartwallet.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // L'administrateur qui a effectué l'action
    @Column(nullable = false)
    private String admin;

    // Description de l'action (ex: "Désactivation d'un compte")
    @Column(nullable = false, length = 500)
    private String action;

    // La cible de l'action (ex: email de l'utilisateur concerné)
    @Column
    private String target;

    // Niveau de gravité: INFO, SUCCES, AVERTISSEMENT, CRITIQUE
    @Column(nullable = false)
    private String status;

    // Date et heure de l'action
    @Column(nullable = false)
    private LocalDateTime date;

    public AuditLog(String admin, String action, String target, String status) {
        this.admin = admin;
        this.action = action;
        this.target = target;
        this.status = status;
        this.date = LocalDateTime.now();
    }
}
