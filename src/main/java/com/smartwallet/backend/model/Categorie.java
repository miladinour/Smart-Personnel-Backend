package com.smartwallet.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;

@Entity
@Table(name = "categories", uniqueConstraints = {
                @UniqueConstraint(columnNames = { "nom", "user_id" })
}) // pour que deux users peux créer une categorie portant le meme nom
@Data
@NoArgsConstructor
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Categorie implements Serializable {

        public Categorie(String nom) {
                this.nom = nom;
        }

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false)
        private String nom;

        private String type; // REVENU or DEPENSE
        // Le champ type permet au Backend d'envoyer seulement les catégories où type =
        // 'DEPENSE'
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "user_id")
        @JsonIgnore
        private User user;

        @OneToMany(mappedBy = "categorie")
        @JsonIgnore
        private List<Transaction> transactions;
}
