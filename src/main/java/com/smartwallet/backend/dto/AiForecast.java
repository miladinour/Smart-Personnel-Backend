package com.smartwallet.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiForecast {
    private String month;
    private String category; // null for total forecast
    private BigDecimal predictedIncome;
    private BigDecimal predictedExpenses;
    private BigDecimal optimisticExpenses;
    private BigDecimal pessimisticExpenses;
    private Double confidence;
    private List<String> insights;
}
