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
})
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

    private String type;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @Column(name = "is_system")
    private Boolean systemCategory = false;

    @com.fasterxml.jackson.annotation.JsonProperty("isDefault")
    public Boolean isSystemCategory() {
        return systemCategory;
    }

    public void setSystemCategory(Boolean systemCategory) {
        this.systemCategory = systemCategory;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("userId")
    public Long getUserId() {
        return user != null ? user.getId() : null;
    }

    @OneToMany(mappedBy = "categorie")
    @JsonIgnore
    private List<Transaction> transactions;
}
