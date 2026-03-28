package com.smartwallet.backend.service;

import com.smartwallet.backend.dto.AiRecommendation;
import com.smartwallet.backend.model.Defi;
import com.smartwallet.backend.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiRecommendationService {

    private final DepenseService depenseService;
    private final RevenuService revenuService;
    private final DefiService defiService;
    private final com.smartwallet.backend.repository.BudgetRepository budgetRepository;

    public List<AiRecommendation> getRecommendations(User user) {
        List<AiRecommendation> recommendations = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        BigDecimal totalDepenses = depenseService.getTotalDepenses(user);
        BigDecimal totalRevenus = revenuService.getTotalRevenus(user);
        Map<String, BigDecimal> stats = depenseService.getDepensesByCategory(user);
        List<Defi> activeDefis = defiService.getActiveDefis(user);

        // 1. Budget Overload Check (Global)
        if (totalDepenses.compareTo(totalRevenus) > 0 && totalRevenus.compareTo(BigDecimal.ZERO) > 0) {
            recommendations.add(new AiRecommendation(
                    "Alerte : Déficit Mensuel",
                    "Vos dépenses totales (" + totalDepenses + ") ont dépassé vos revenus (" + totalRevenus
                            + "). Attention à votre épargne !",
                    "warning",
                    "Global",
                    false,
                    null));
        }

        // 1b. Real Budgets Check (per category)
        java.time.LocalDate today = java.time.LocalDate.now();
        budgetRepository.findByUser(user).stream()
                .filter(b -> !b.getDateDebut().isAfter(today) && !b.getDateFin().isBefore(today))
                .forEach(budget -> {
                    java.time.LocalDateTime startDateTime = budget.getDateDebut().atStartOfDay();
                    java.time.LocalDateTime endDateTime = budget.getDateFin().atTime(java.time.LocalTime.MAX);
                    List<com.smartwallet.backend.model.Depense> deps = depenseService.getDepensesByUser(user, startDateTime, endDateTime, budget.getCategorie() != null ? budget.getCategorie().getId() : null);
                    BigDecimal currentAmount = deps.stream().map(com.smartwallet.backend.model.Depense::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal limit = budget.getMontantLimite();
                    if (currentAmount.compareTo(limit) >= 0) {
                        recommendations.add(new AiRecommendation(
                                "Budget Dépassé : "
                                        + (budget.getCategorie() != null ? budget.getCategorie().getNom() : "Général"),
                                "Vous avez dépassé votre budget de " + limit + " " + user.getDevise()
                                        + ". Limitez vos dépenses dans cette catégorie.",
                                "danger",
                                budget.getCategorie() != null ? budget.getCategorie().getNom() : "Budget",
                                false,
                                null));
                    } else if (currentAmount.compareTo(limit.multiply(new BigDecimal("0.8"))) >= 0) {
                        recommendations.add(new AiRecommendation(
                                "Budget Presque Atteint",
                                "Vous avez consommé plus de 80% de votre budget pour "
                                        + (budget.getCategorie() != null ? budget.getCategorie().getNom()
                                                : "cette catégorie")
                                        + ".",
                                "warning",
                                budget.getCategorie() != null ? budget.getCategorie().getNom() : "Budget",
                                false,
                                null));
                    }
                });

        // 2. Trend Analysis (Current vs Previous Month)
        int lastMonth = now.minusMonths(1).getMonthValue();
        int lastYear = now.minusMonths(1).getYear();
        BigDecimal currentMonthExpenses = depenseService.getTotalDepensesForMonth(user, now.getMonthValue(),
                now.getYear());
        BigDecimal lastMonthExpenses = depenseService.getTotalDepensesForMonth(user, lastMonth, lastYear);

        if (lastMonthExpenses.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal increase = currentMonthExpenses.subtract(lastMonthExpenses)
                    .divide(lastMonthExpenses, 2, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal(100));
            if (increase.compareTo(new BigDecimal(20)) > 0) {
                recommendations.add(new AiRecommendation(
                        "Hausse des Dépenses",
                        "Vos dépenses ont augmenté de " + increase
                                + "% par rapport au mois dernier. Vérifiez vos nouveaux achats.",
                        "warning",
                        "Tendances",
                        false,
                        null));
            }
        }

        // 3. Emergency Fund Check
        BigDecimal avgExpenses = depenseService.getAverageMonthlyExpenses(user, 3);
        BigDecimal targetBuffer = avgExpenses.multiply(new BigDecimal(3));
        if (user.getSoldeTotal().compareTo(targetBuffer) < 0 && avgExpenses.compareTo(BigDecimal.ZERO) > 0) {
            recommendations.add(new AiRecommendation(
                    "Sécurité Financière",
                    "Votre épargne actuelle est inférieure à 3 mois de dépenses moyennes (" + targetBuffer + " "
                            + user.getDevise() + "). Essayez de renforcer votre fonds d'urgence.",
                    "info",
                    "Épargne",
                    false,
                    null));
        }

        // 4. Detailed Analysis via specialized methods
        checkCategoryAdvice(user, totalDepenses, totalRevenus, stats, activeDefis, recommendations);
        checkEmergencyFund(user, lastMonthExpenses, recommendations);
        checkAnomalyAdvice(user, totalDepenses, totalRevenus, recommendations);
        checkPositiveAdvice(user, recommendations);

        // 5. Default if empty
        if (recommendations.isEmpty()) {
            recommendations.add(new AiRecommendation(
                    "Analyse Complète",
                    "Votre gestion financière semble stable. Continuez à enregistrer vos transactions pour des conseils plus précis.",
                    "success",
                    "Général",
                    false,
                    null));
        }

        return recommendations;
    }

    private void checkCategoryAdvice(User user, BigDecimal totalDep, BigDecimal totalRev, Map<String, BigDecimal> stats,
            List<Defi> activeDefis, List<AiRecommendation> recommendations) {
        for (Map.Entry<String, BigDecimal> entry : stats.entrySet()) {
            String cat = entry.getKey();
            BigDecimal amount = entry.getValue();

            boolean hasActiveDefi = activeDefis.stream().anyMatch(d -> d.getCategorieCible().equalsIgnoreCase(cat));

            if ("Alimentation".equalsIgnoreCase(cat) && amount.compareTo(new BigDecimal(700)) > 0) {
                String title = hasActiveDefi ? "Budget Alimentaire" : "Défi : Semaine sans Fast-Food";
                String msg = hasActiveDefi ? "Vous avez dépensé " + amount
                        + " en nourriture. Saviez-vous que préparer vos repas peut réduire cette dépense de 25% ?"
                        : "Vos dépenses alimentaires sont élevées (" + amount
                                + "). Relevez le défi : pas de fast-food pendant 7 jours !";
                recommendations
                        .add(new AiRecommendation(title, msg, "info", "Alimentation", !hasActiveDefi, "Alimentation"));
            } else if ("Shopping".equalsIgnoreCase(cat) && amount.compareTo(new BigDecimal(300)) > 0) {
                String title = hasActiveDefi ? "Shopping" : "Défi : Mois sans Shopping";
                String msg = hasActiveDefi ? "Vérifiez vos achats de ce mois."
                        : "Près de " + amount
                                + " en shopping ! Relevez le défi : aucun achat non-essentiel ce mois-ci.";
                recommendations.add(new AiRecommendation(title, msg, "info", "Shopping", !hasActiveDefi, "Shopping"));
            } else if ("Loisirs".equalsIgnoreCase(cat)) {
                if (amount.compareTo(totalDep.multiply(new BigDecimal("0.3"))) > 0) {
                    recommendations.add(new AiRecommendation("Équilibre Loisirs",
                            "Attention : Votre budget Loisirs dépasse 30% de vos dépenses totales.", "warning",
                            "Loisirs", false, null));
                }
            }
        }

        // Rule for Subscriptions (Services)
        BigDecimal services = stats.getOrDefault("Services", BigDecimal.ZERO)
                .add(stats.getOrDefault("Abonnements", BigDecimal.ZERO));
        if (services.compareTo(new BigDecimal(100)) > 0) {
            recommendations
                    .add(new AiRecommendation("Optimisation Services",
                            "Vous dépensez pas mal en services/abonnements (" + services
                                    + "). Vérifiez s'il n'y a pas des doublons inutiles.",
                            "info", "Général", false, null));
        }
    }

    private void checkEmergencyFund(User user, BigDecimal lastMonthExpenses, List<AiRecommendation> recommendations) {
        BigDecimal avgExpenses = depenseService.getAverageMonthlyExpenses(user, 3);
        BigDecimal targetBuffer = avgExpenses.multiply(new BigDecimal(3));
        BigDecimal currentSolde = user.getSoldeTotal() != null ? user.getSoldeTotal() : BigDecimal.ZERO;
        if (avgExpenses.compareTo(BigDecimal.ZERO) > 0 && currentSolde.compareTo(targetBuffer) < 0) {
            recommendations.add(new AiRecommendation(
                    "Sécurité Financière", "Votre épargne actuelle est inférieure à 3 mois de dépenses moyennes ("
                            + targetBuffer + "). Essayez de renforcer votre fonds d'urgence.",
                    "info", "Épargne", false, null));
        }

    }

    private void checkAnomalyAdvice(User user, BigDecimal totalDep, BigDecimal totalRev,
            List<AiRecommendation> recommendations) {
        if (totalDep.compareTo(totalRev) > 0 && totalRev.compareTo(BigDecimal.ZERO) > 0) {
            // Already handled by Budget Overload Check, but we can add more specific
            // anomaly detection here if needed.
        }
    }

    private void checkPositiveAdvice(User user, List<AiRecommendation> recommendations) {
        BigDecimal currentSolde = user.getSoldeTotal() != null ? user.getSoldeTotal() : BigDecimal.ZERO;
        
        // Suggest creating an objective if balance is healthy
        if (currentSolde.compareTo(new BigDecimal(1000)) > 0) {
             recommendations.add(new AiRecommendation(
                 "Objectif d'Épargne", 
                 "Votre solde global est de " + currentSolde + " " + user.getDevise() + ". C'est le moment idéal pour créer un nouvel objectif financier (ex: Voyage, Voiture) !", 
                 "success", 
                 "Épargne", 
                 false, 
                 null));
                 
             recommendations.add(new AiRecommendation(
                 "Allocation Intelligente", 
                 "Vous avez une bonne marge budgétaire. Envisagez d'ajouter une partie de vos fonds excédentaires à un budget 'Santé' ou 'Investissement'.", 
                 "info", 
                 "Optimisation", 
                 false, 
                 null));
        } else if (currentSolde.compareTo(new BigDecimal(5000)) > 0) {
             recommendations.add(new AiRecommendation(
                 "Excellent Travail", 
                 "Votre solde total est très solide ! Continuez sur cette voie pour assurer votre indépendance financière.", 
                 "success", 
                 "Général", 
                 false, 
                 null));
        }
    }
}
