package com.smartwallet.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeasonalAlert {
    private String eventName;        // e.g. "Aïd el-Fitr", "Rentrée Scolaire"
    private String month;            // e.g. "Mars 2025"
    private String icon;             // emoji icon
    private String description;      // Human-readable warning message
    private BigDecimal estimatedSurplus; // Extra budget to plan (based on history)
    private int historicalIncreasePercent; // e.g. 35 → spending was 35% higher last year
    private boolean isHighRisk;      // true if historically very high spend
}
