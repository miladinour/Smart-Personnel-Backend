package com.smartwallet.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetResponse {
    private Long id;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private BigDecimal montantLimite;
    private BigDecimal currentAmount;
    private Long categorieId;
    private String categorieNom;
    private Long userId;
    private String type; // GLOBAL or CATEGORIE
}
