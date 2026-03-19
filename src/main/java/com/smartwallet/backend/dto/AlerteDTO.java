package com.smartwallet.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AlerteDTO {
    private Long id;
    private LocalDateTime date;
    private String message;
    private boolean conditionVerifiee;
    private Long budgetId;
    private String categorieNom;
}
