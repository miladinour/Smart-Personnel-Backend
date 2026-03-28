package com.smartwallet.backend.service;

import com.smartwallet.backend.dto.AiForecast;
import com.smartwallet.backend.model.Depense;
import com.smartwallet.backend.model.Revenu;
import com.smartwallet.backend.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiForecastService {

    private final DepenseService depenseService;
    private final RevenuService revenuService;

    public List<AiForecast> getForecasts(User user) {
        System.out.println(">>> AI Forecast - Processing for user: " + user.getEmail());
        List<AiForecast> forecasts = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneMonthAgo = now.minusDays(30);

        try {
            // 1. Fetch data for analysis (Last 30 days)
            List<Depense> recentDepenses = depenseService.getDepensesByUser(user, oneMonthAgo, now, null);
            List<Revenu> recentRevenus = revenuService.getRevenusByUser(user, oneMonthAgo, now, null);
            System.out.println(">>> AI Forecast - Found " + recentDepenses.size() + " expenses and " + recentRevenus.size() + " revenues");

            BigDecimal totalExpenses = recentDepenses.stream()
                    .map(Depense::getMontant)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalIncome = recentRevenus.stream()
                    .map(Revenu::getMontant)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Daily averages as base for projection
            BigDecimal dailyExpenseAvg = totalExpenses.divide(new BigDecimal(30), 2, RoundingMode.HALF_UP);
            BigDecimal dailyIncomeAvg = totalIncome.divide(new BigDecimal(30), 2, RoundingMode.HALF_UP);

            // 2. Generate forecasts for the next 3 months
            for (int i = 1; i <= 3; i++) {
                LocalDateTime targetMonth = now.plusMonths(i);
                String monthName = targetMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH) + " " + targetMonth.getYear();
                
                // Basic projection: daily avg * 30 (simplified)
                BigDecimal predictedExpenses = dailyExpenseAvg.multiply(new BigDecimal(30));
                BigDecimal predictedIncome = dailyIncomeAvg.multiply(new BigDecimal(30));
                
                // Add some variation for realism (e.g., slight growth)
                predictedExpenses = predictedExpenses.multiply(new BigDecimal(1.0 + (i * 0.05))); 

                List<String> insights = generateInsights(predictedIncome, predictedExpenses, user);

                BigDecimal optimisticExpenses = predictedExpenses.multiply(new BigDecimal(0.85));
                BigDecimal pessimisticExpenses = predictedExpenses.multiply(new BigDecimal(1.15));

                forecasts.add(new AiForecast(
                    monthName,
                    null, // Global
                    predictedIncome.setScale(2, RoundingMode.HALF_UP),
                    predictedExpenses.setScale(2, RoundingMode.HALF_UP),
                    optimisticExpenses.setScale(2, RoundingMode.HALF_UP),
                    pessimisticExpenses.setScale(2, RoundingMode.HALF_UP),
                    0.85 - (i * 0.05),
                    insights
                ));

                // 3. Category-specific forecasts (Top 3) - Based on RECENT expenses
                Map<String, BigDecimal> categoryRecentStats = new HashMap<>();
                for (Depense d : recentDepenses) {
                    if (d.getCategorie() != null && d.getCategorie().getNom() != null) {
                        String catName = d.getCategorie().getNom();
                        categoryRecentStats.put(catName, categoryRecentStats.getOrDefault(catName, BigDecimal.ZERO).add(d.getMontant()));
                    }
                }

                final int monthIndex = i; // Fix: effectively final for lambda
                categoryRecentStats.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(3)
                    .forEach(entry -> {
                        String categoryName = entry.getKey();
                        BigDecimal monthlyAvg = entry.getValue(); 
                        
                        BigDecimal predictedCatEx = monthlyAvg.multiply(BigDecimal.valueOf(1.0 + (monthIndex * 0.03))); 
                        
                        List<String> catInsights = new ArrayList<>();
                        catInsights.add("Analyse IA : Pour '" + categoryName + "', vos dépenses prévues en " + targetMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH) + " sont de " + predictedCatEx.setScale(0, RoundingMode.HALF_UP) + " DT.");
                        
                        BigDecimal optimisticCatEx = predictedCatEx.multiply(BigDecimal.valueOf(0.9));
                        BigDecimal pessimisticCatEx = predictedCatEx.multiply(BigDecimal.valueOf(1.1));

                        forecasts.add(new AiForecast(
                            monthName,
                            categoryName,
                            BigDecimal.ZERO,
                            predictedCatEx.setScale(2, RoundingMode.HALF_UP),
                            optimisticCatEx.setScale(2, RoundingMode.HALF_UP),
                            pessimisticCatEx.setScale(2, RoundingMode.HALF_UP),
                            0.75 - (monthIndex * 0.05),
                            catInsights
                        ));
                    });
            }
        } catch (Exception e) {
            System.err.println(">>> AI Forecast - Error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println(">>> AI Forecast - Returning " + forecasts.size() + " forecasts");
        return forecasts;
    }

    private List<String> generateInsights(BigDecimal income, BigDecimal expenses, User user) {
        List<String> insights = new ArrayList<>();
        BigDecimal balance = income.subtract(expenses);
        BigDecimal annualProjection = expenses.multiply(new BigDecimal(12));

        // specific wording requested by user
        insights.add("Selon vos dépenses actuelles, votre solde estimé à la fin du mois sera de " + balance.setScale(0, RoundingMode.HALF_UP) + " DT.");
        
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            insights.add("Attention : Votre comportement actuel pourrait mener à un découvert.");
        } else {
            insights.add("Vous pouvez économiser environ " + balance.multiply(new BigDecimal(0.3)).setScale(0, RoundingMode.HALF_UP) + " DT ce mois-ci si vous limitez les dépenses non essentielles.");
        }

        insights.add("Si vos habitudes restent identiques, vos dépenses annuelles seront de " + annualProjection.setScale(0, RoundingMode.HALF_UP) + " DT.");

        return insights;
    }
}
