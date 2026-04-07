package com.smartwallet.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.io.Serializable;


@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "transaction_type", discriminatorType = DiscriminatorType.STRING)
@Table(name = "transactions")
@Data
@NoArgsConstructor
public abstract class Transaction implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal montant;

    private String description;


    @Column(name = "date")
    private LocalDateTime date;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id", nullable = true)
    private Categorie categorie;

    @Column(name = "is_recurring")
    private Boolean isRecurring = false;

    public boolean isRecurring() {
        return isRecurring != null && isRecurring;
    }

    public void setRecurring(boolean recurring) {
        this.isRecurring = recurring;
    }

    public abstract void calculerTotal();
}
