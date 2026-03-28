package com.smartwallet.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "defis")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Defi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String titre;
    private String description;
    private String categorieCible; // La catégorie à surveiller (ex: "Shopping")
    
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    
    private boolean active = true;
    private boolean failed = false;
    private boolean success = false;

    // Helper to check if still valid
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(dateFin);
    }
}
