package com.smartwallet.backend.service;

import com.smartwallet.backend.dto.AiRecommendation;
import com.smartwallet.backend.model.Defi;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.model.Budget;
import com.smartwallet.backend.model.Depense;
import com.smartwallet.backend.repository.BudgetRepository;
import com.smartwallet.backend.repository.AiRecommendationHistoryRepository;
import com.smartwallet.backend.model.AiRecommendationHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AiRecommendationService {

    private final DepenseService depenseService;
    private final RevenuService revenuService;
    private final DefiService defiService;
    private final ObjectifService objectifService;
    private final DetteService detteService;
    private final AiForecastService aiForecastService;
    private final BudgetRepository budgetRepository;
    private final AiRecommendationHistoryRepository aiRecommendationHistoryRepository;

    public List<AiRecommendation> getRecommendations(User user) {
        List<AiRecommendation> recommendations = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        BigDecimal totalDepenses = depenseService.getTotalDepenses(user);
        BigDecimal totalRevenus = revenuService.getTotalRevenus(user);
        Map<String, BigDecimal> stats = depenseService.getDepensesByCategory(user);
        List<Defi> activeDefis = defiService.getActiveDefis(user);

        final boolean isNewUser = totalRevenus.compareTo(BigDecimal.ZERO) == 0 && totalDepenses.compareTo(BigDecimal.ZERO) == 0;

        // 1. Welcome / Onboarding (FOR NEW USERS)
        if (isNewUser) {
            checkWelcomeAdvice(user, recommendations);
        } else {
            // 1b. Balance Check (Success or Danger) - ONLY IF NOT NEW
            if (totalDepenses.compareTo(totalRevenus) > 0 && totalRevenus.compareTo(BigDecimal.ZERO) > 0) {
                recommendations.add(new AiRecommendation(
                    "ai.rec.negative_balance_title",
                    "ai.rec.negative_balance_message",
                    "danger",
                    "Global",
                    "ai.rec.negative_balance_explanation",
                    false,
                    null
                ));
            } else if (totalRevenus.compareTo(BigDecimal.ZERO) > 0) {
                recommendations.add(new AiRecommendation(
                    "ai.rec.positive_balance_title",
                    "ai.rec.positive_balance_message",
                    "success",
                    "Global",
                    "ai.rec.positive_balance_explanation",
                    false,
                    null
                ));
            }
        }

        // 2. Budget Monitoring (Only if active budgets exist)
        LocalDate today = LocalDate.now();
        budgetRepository.findByUser(user).stream()
            .filter(b -> !b.getDateDebut().isAfter(today) && !b.getDateFin().isBefore(today))
            .forEach(budget -> {
                String catName = budget.getCategorie() != null ? budget.getCategorie().getNom() : "Général";
                BigDecimal limit = budget.getMontantLimite();
                
                List<Depense> deps = depenseService.getDepensesByUser(user, 
                    budget.getDateDebut().atStartOfDay(), 
                    budget.getDateFin().atTime(LocalTime.MAX), 
                    budget.getCategorie() != null ? budget.getCategorie().getId() : null);
                
                BigDecimal spent = deps.stream().map(Depense::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
                boolean hasActiveDefi = activeDefis.stream().anyMatch(d -> d.getCategorieCible().equalsIgnoreCase(catName));

                Map<String, String> budgetParams = new HashMap<>();
                budgetParams.put("category", catName);
                budgetParams.put("limit", limit.toString());

                if (spent.compareTo(limit) >= 0) {
                    recommendations.add(new AiRecommendation(
                        "ai.rec.budget_reached_title",
                        "ai.rec.budget_reached_message",
                        "danger",
                        catName,
                        hasActiveDefi ? "ai.rec.budget_reached_with_defi_explanation" : "ai.rec.budget_reached_explanation",
                        !hasActiveDefi,
                        catName,
                        budgetParams
                    ));
                } else if (spent.compareTo(limit.multiply(new BigDecimal("0.8"))) >= 0) {
                    recommendations.add(new AiRecommendation(
                        "ai.rec.budget_warning_title",
                        "ai.rec.budget_warning_message",
                        "warning",
                        catName,
                        "ai.rec.budget_warning_explanation",
                        false,
                        null,
                        budgetParams
                    ));
                }
            });

        // 3. Trends & Habit-based Advice (Only if NOT new)
        if (!isNewUser) {
            checkTrendAdvice(user, now, recommendations);
            checkCategoryAdvice(user, totalDepenses, stats, activeDefis, recommendations);
            checkForecastAdvice(user, recommendations);
            checkSalaryAllocation(user, recommendations); // Le Saviez-Vous ?
            checkChallengeIncentive(user, activeDefis, stats, recommendations); // Prêt pour un Défi ?
        }

        // 4. General / Specialized Logic (Safe for all)
        checkEmergencyFund(user, isNewUser, recommendations);
        checkPositiveAdvice(user, recommendations);
        checkSmartBudgetAllocation(user, recommendations);
        checkSavingsPotential(user, recommendations);
        checkDebtAdvice(user, recommendations);

        // Process History to freeze timestamps
        for (AiRecommendation rec : recommendations) {
            Optional<AiRecommendationHistory> existing = aiRecommendationHistoryRepository
                .findTopByUserAndTitleOrderByDateCreationDesc(user, rec.getTitle());
            
            if (existing.isPresent()) {
                LocalDateTime created = existing.get().getDateCreation();
                // Consider as a new alert if the last one was over 30 days ago
                if (created.isBefore(LocalDateTime.now().minusDays(30))) {
                    AiRecommendationHistory newHistory = new AiRecommendationHistory(user, rec.getTitle(), rec.getCategory(), LocalDateTime.now());
                    aiRecommendationHistoryRepository.save(newHistory);
                    rec.setTimestamp(newHistory.getDateCreation());
                } else {
                    rec.setTimestamp(created);
                }
            } else {
                AiRecommendationHistory newHistory = new AiRecommendationHistory(user, rec.getTitle(), rec.getCategory(), LocalDateTime.now());
                aiRecommendationHistoryRepository.save(newHistory);
                rec.setTimestamp(newHistory.getDateCreation());
            }
        }

        return recommendations;
    }

    private void checkTrendAdvice(User user, LocalDateTime now, List<AiRecommendation> recommendations) {
        int lastMonth = now.minusMonths(1).getMonthValue();
        int lastYear = now.minusMonths(1).getYear();
        BigDecimal lastMonthExpenses = depenseService.getTotalDepensesForMonth(user, lastMonth, lastYear);
        BigDecimal currentMonthExpenses = depenseService.getTotalDepensesForMonth(user, now.getMonthValue(), now.getYear());

        if (lastMonthExpenses.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal increase = currentMonthExpenses.subtract(lastMonthExpenses)
                .divide(lastMonthExpenses, 2, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(100));

            Map<String, String> trendParams = new HashMap<>();
            trendParams.put("percent", increase.abs().toString());

            if (increase.compareTo(new BigDecimal(20)) > 0) {
                recommendations.add(new AiRecommendation(
                    "ai.rec.trend_up_title",
                    "ai.rec.trend_up_message",
                    "info",
                    "Tendances",
                    "ai.rec.trend_up_explanation",
                    false,
                    null,
                    trendParams
                ));
            } else if (increase.compareTo(new BigDecimal("-10")) < 0) {
                 recommendations.add(new AiRecommendation(
                    "ai.rec.trend_down_title",
                    "ai.rec.trend_down_message",
                    "success",
                    "Tendances",
                    "ai.rec.trend_down_explanation",
                    false,
                    null,
                    trendParams
                ));
            }
        }
    }

    private void checkWelcomeAdvice(User user, List<AiRecommendation> recommendations) {
        recommendations.add(new AiRecommendation(
            "ai.rec.welcome_title",
            "ai.rec.welcome_message",
            "success",
            "Onboarding",
            "ai.rec.welcome_explanation",
            false,
            null
        ));
        
        recommendations.add(new AiRecommendation(
            "ai.rec.first_step_title",
            "ai.rec.first_step_message",
            "info",
            "Education",
            "ai.rec.first_step_explanation",
            false,
            null
        ));
    }

    private void checkCategoryAdvice(User user, BigDecimal totalDep, Map<String, BigDecimal> stats, List<Defi> activeDefis, List<AiRecommendation> recommendations) {
        for (Map.Entry<String, BigDecimal> entry : stats.entrySet()) {
            String cat = entry.getKey();
            BigDecimal amount = entry.getValue();
            boolean hasActiveDefi = activeDefis.stream().anyMatch(d -> d.getCategorieCible().equalsIgnoreCase(cat));

            if ("Alimentation".equalsIgnoreCase(cat) && amount.compareTo(new BigDecimal(500)) > 0) {
                Map<String, String> foodParams = new HashMap<>();
                foodParams.put("amount", amount.toString());
                foodParams.put("currency", user.getDevise());

                recommendations.add(new AiRecommendation(
                    hasActiveDefi ? "ai.rec.food_tracking_title" : "ai.rec.meal_prep_title",
                    "ai.rec.meal_prep_message",
                    "info",
                    "Alimentation",
                    "ai.rec.meal_prep_explanation",
                    !hasActiveDefi,
                    "Alimentation",
                    foodParams
                ));
            } else if ("Loisirs".equalsIgnoreCase(cat) && totalDep.compareTo(BigDecimal.ZERO) > 0) {
                 if (amount.compareTo(totalDep.multiply(new BigDecimal("0.3"))) > 0) {
                    recommendations.add(new AiRecommendation(
                        "ai.rec.leisure_focus_title",
                        "ai.rec.leisure_focus_message",
                        "info",
                        "Loisirs",
                        "ai.rec.leisure_focus_explanation",
                        false,
                        null
                    ));
                 }
            }
        }

        // Subscriptions cleanup
        BigDecimal services = stats.getOrDefault("Services", BigDecimal.ZERO)
                .add(stats.getOrDefault("Abonnements", BigDecimal.ZERO));
        if (services.compareTo(new BigDecimal(80)) > 0) {
            Map<String, String> subParams = new HashMap<>();
            subParams.put("amount", services.toString());
            subParams.put("currency", user.getDevise());

            recommendations.add(new AiRecommendation(
                "ai.rec.subscription_audit_title",
                "ai.rec.subscription_audit_message",
                "info",
                "Optimisation",
                "ai.rec.subscription_audit_explanation",
                false,
                null,
                subParams
            ));
        }
    }

    private void checkEmergencyFund(User user, boolean isNewUser, List<AiRecommendation> recommendations) {
        BigDecimal currentSolde = user.getSoldeTotal() != null ? user.getSoldeTotal() : BigDecimal.ZERO;
        if (currentSolde.compareTo(new BigDecimal(500)) < 0) {
             recommendations.add(new AiRecommendation(
                isNewUser ? "ai.rec.emergency_fund_new_title" : "ai.rec.emergency_fund_title",
                isNewUser ? "ai.rec.emergency_fund_new_message" : "ai.rec.emergency_fund_message",
                isNewUser ? "info" : "danger",
                "\u00C9pargne",
                "ai.rec.emergency_fund_explanation",
                false,
                null
            ));
        }
    }

    private void checkPositiveAdvice(User user, List<AiRecommendation> recommendations) {
        BigDecimal solde = user.getSoldeTotal() != null ? user.getSoldeTotal() : BigDecimal.ZERO;
        if (solde.compareTo(new BigDecimal(3000)) > 0) {
            Map<String, String> milestoneParams = new HashMap<>();
            milestoneParams.put("currency", user.getDevise());

            recommendations.add(new AiRecommendation(
                "ai.rec.milestone_3k_title",
                "ai.rec.milestone_3k_message",
                "success",
                "Global",
                "ai.rec.milestone_3k_explanation",
                false,
                null,
                milestoneParams
            ));
        }
    }

    private void checkSmartBudgetAllocation(User user, List<AiRecommendation> recommendations) {
        recommendations.add(new AiRecommendation(
            "ai.rec.golden_rule_title",
            "ai.rec.golden_rule_message",
            "success",
            "Education",
            "ai.rec.golden_rule_explanation",
            false,
            null
        ));
    }

    private void checkSavingsPotential(User user, List<AiRecommendation> recommendations) {
        LocalDateTime now = LocalDateTime.now();
        if (now.getDayOfMonth() > 20) {
             recommendations.add(new AiRecommendation(
                "ai.rec.end_month_title",
                "ai.rec.end_month_message",
                "info",
                "\u00C9pargne",
                "ai.rec.end_month_explanation",
                false,
                null
            ));
        }
    }

    private void checkDebtAdvice(User user, List<AiRecommendation> recommendations) {
        if (!detteService.getActiveDettes(user).isEmpty()) {
            recommendations.add(new AiRecommendation(
                "ai.rec.financial_freedom_title",
                "ai.rec.financial_freedom_message",
                "info",
                "Dettes",
                "ai.rec.financial_freedom_explanation",
                false,
                null
            ));
        }
    }

    private void checkForecastAdvice(User user, List<AiRecommendation> recommendations) {
        aiForecastService.getForecasts(user).stream().findFirst().ifPresent(f -> {
             recommendations.add(new AiRecommendation(
                "ai.rec.future_vision_title",
                "ai.rec.future_vision_message",
                "info",
                "Prévisions",
                "ai.rec.future_vision_explanation",
                false,
                null
            ));
        });
    }

    private void checkSalaryAllocation(User user, List<AiRecommendation> recommendations) {
        // Just a constant tip to keep the screen varied
        recommendations.add(new AiRecommendation(
            "ai.rec.did_you_know_title",
            "ai.rec.did_you_know_message",
            "success",
            "Education",
            "ai.rec.did_you_know_explanation",
            false,
            null
        ));
    }

    private void checkChallengeIncentive(User user, List<Defi> activeDefis, Map<String, BigDecimal> stats, List<AiRecommendation> recommendations) {
        if (activeDefis.isEmpty()) {
             recommendations.add(new AiRecommendation(
                "ai.rec.challenge_ready_title",
                "ai.rec.challenge_ready_message",
                "info",
                "Défis",
                "ai.rec.challenge_ready_explanation",
                true,
                "Global"
            ));
        }
    }
}
