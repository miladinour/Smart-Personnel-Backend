package com.smartwallet.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "administrateur")
@PrimaryKeyJoinColumn(name = "id")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Administrateur extends Personne {

    public void gererUtilisateur() {
        // Logique de gestion
    }

    public void superviserPlateforme() {
        // Logique de supervision
    }
}
