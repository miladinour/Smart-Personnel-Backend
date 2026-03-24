package com.smartwallet.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.lang.String;
import java.lang.Long;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class Personne implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String motDePasse;

    @Column(nullable = true)
    private String nom;

    @Column(nullable = true)
    private String prenom;

    public java.lang.Long getId() {
        return this.id;
    }

    public java.lang.String getEmail() {
        return this.email;
    }

    public java.lang.String getMotDePasse() {
        return this.motDePasse;
    }

    public java.lang.String getNom() {
        return this.nom;
    }

    public java.lang.String getPrenom() {
        return this.prenom;
    }
}
