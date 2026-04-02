package com.smartwallet.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertResponse {
    private Long id;
    private LocalDateTime date;
    private String message;
    private boolean conditionVerifiee;
    private Long budgetId;
    private String categorieNom;
}
