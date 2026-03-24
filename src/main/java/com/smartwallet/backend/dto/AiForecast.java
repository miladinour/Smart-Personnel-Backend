package com.smartwallet.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data

@NoArgsConstructor
public class AiForecast {
    private String month;
    private String category; // null for total forecast
    private BigDecimal predictedIncome;
    private BigDecimal predictedExpenses;
    private Double confidence;
    private List<String> insights;

    public AiForecast(String month, String category, BigDecimal predictedIncome, 
                      BigDecimal predictedExpenses, Double confidence, List<String> insights) {
        this.month = month;
        this.category = category;
        this.predictedIncome = predictedIncome;
        this.predictedExpenses = predictedExpenses;
        this.confidence = confidence;
        this.insights = insights;
    }
}
