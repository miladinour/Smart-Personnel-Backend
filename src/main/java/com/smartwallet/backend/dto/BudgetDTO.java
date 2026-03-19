package com.smartwallet.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BudgetDTO {
    private Long id;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private BigDecimal montantLimite;
    private double currentAmount = 0.0; 
    private Long categorieId;
    private String categorieNom;
    private Long userId;
    private String type; 
}
