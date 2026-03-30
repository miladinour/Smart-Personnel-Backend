package com.smartwallet.backend.service;

import com.smartwallet.backend.dto.AiRecommendation;
import com.smartwallet.backend.model.Defi;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.model.Budget;
import com.smartwallet.backend.model.Depense;
import com.smartwallet.backend.repository.BudgetRepository;
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

    public List<AiRecommendation> getRecommendations(User user) {
        List<AiRecommendation> recommendations = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        BigDecimal totalDepenses = depenseService.getTotalDepenses(user);
        BigDecimal totalRevenus = revenuService.getTotalRevenus(user);
        Map<String, BigDecimal> stats = depenseService.getDepensesByCategory(user);
        List<Defi> activeDefis = defiService.getActiveDefis(user);

        // 1. Balance Check (Success or Danger)
        if (totalDepenses.compareTo(totalRevenus) > 0 && totalRevenus.compareTo(BigDecimal.ZERO) > 0) {
            recommendations.add(new AiRecommendation(
                "Alerte : Solde N\u00E9gatif",
                "Vos d\u00E9penses (" + totalDepenses + ") d\u00E9passent vos revenus.",
                "danger",
                "Global",
                "Votre balance mensuelle est n\u00E9gative. Cela impacte directement votre \u00E9pargne. Essayez de limiter les achats non-essentiels cette semaine.",
                false,
                null
            ));
        } else if (totalRevenus.compareTo(BigDecimal.ZERO) > 0) {
            recommendations.add(new AiRecommendation(
                "Gestion Ma\u00EEtris\u00E9e",
                "F\u00E9licitations ! Vous d\u00E9pensez moins que ce que vous gagnez.",
                "success",
                "Global",
                "C'est la base d'une bonne sant\u00E9 financi\u00E8re. Vous pourriez allouer une partie de votre surplus \u00E0 un objectif d'\u00E9pargne.",
                false,
                null
            ));
        }

        // 2. Budget Monitoring
        LocalDate today = LocalDate.now();
        budgetRepository.findByUser(user).stream()
            .filter(b -> !b.getDateDebut().isAfter(today) && !b.getDateFin().isBefore(today))
            .forEach(budget -> {
                String catName = budget.getCategorie() != null ? budget.getCategorie().getNom() : "G\u00E9n\u00E9ral";
                BigDecimal limit = budget.getMontantLimite();
                
                List<Depense> deps = depenseService.getDepensesByUser(user, 
                    budget.getDateDebut().atStartOfDay(), 
                    budget.getDateFin().atTime(LocalTime.MAX), 
                    budget.getCategorie() != null ? budget.getCategorie().getId() : null);
                
                BigDecimal spent = deps.stream().map(Depense::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
                boolean hasActiveDefi = activeDefis.stream().anyMatch(d -> d.getCategorieCible().equalsIgnoreCase(catName));

                if (spent.compareTo(limit) >= 0) {
                    recommendations.add(new AiRecommendation(
                        "Budget D\u00E9pass\u00E9 : " + catName,
                        "Limite de " + limit + " atteinte pour " + catName + ".",
                        "danger",
                        catName,
                        hasActiveDefi ? "Budget d\u00E9pass\u00E9, mais d\u00E9fi en cours. Continuez vos efforts !" : "Nous vous sugg\u00E9rons de lancer un d\u00E9fi de 7 jours pour ralentir vos d\u00E9penses dans cette cat\u00E9gorie.",
                        !hasActiveDefi,
                        catName
                    ));
                } else if (spent.compareTo(limit.multiply(new BigDecimal("0.8"))) >= 0) {
                    recommendations.add(new AiRecommendation(
                        "Attention : Budget " + catName,
                        "Vous avez consomm\u00E9 80% de votre budget.",
                        "warning",
                        catName,
                        "Soyez vigilant sur vos prochaines d\u00E9penses dans cette cat\u00E9gorie pour ne pas finir le mois dans le rouge.",
                        false,
                        null
                    ));
                }
            });

        // 3. Trends (Info type)
        checkTrendAdvice(user, now, recommendations);

        // 4. Specialized Logic (RESTORED WITH BETTER TYPES)
        checkCategoryAdvice(user, totalDepenses, stats, activeDefis, recommendations);
        checkEmergencyFund(user, recommendations);
        checkPositiveAdvice(user, recommendations);
        checkSmartBudgetAllocation(user, recommendations);
        checkSavingsPotential(user, recommendations);
        checkDebtAdvice(user, recommendations);
        checkForecastAdvice(user, recommendations);
        checkSalaryAllocation(user, recommendations);
        checkChallengeIncentive(user, activeDefis, stats, recommendations);

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
            if (increase.compareTo(new BigDecimal(20)) > 0) {
                recommendations.add(new AiRecommendation(
                    "Tendance : Hausse d'Activit\u00E9",
                    "Hausse de " + increase + "% par rapport au mois dernier.",
                    "info", // Info instead of warning
                    "Tendances",
                    "Une augmentation notable a \u00E9t\u00E9 d\u00E9tect\u00E9e. Si ce n'est pas un achat pr\u00E9vu, surveillez vos automatismes de d\u00E9pense.",
                    false,
                    null
                ));
            } else if (increase.compareTo(new BigDecimal("-10")) < 0) {
                 recommendations.add(new AiRecommendation(
                    "Tendance : \u00C9conomies",
                    "D\u00E9penses en baisse de " + increase.abs() + "% !",
                    "success",
                    "Tendances",
                    "Bravo, vous progressez dans votre discipline financi\u00E8re. Continuez sur cette lanc\u00E9e.",
                    false,
                    null
                ));
            }
        }
    }

    private void checkCategoryAdvice(User user, BigDecimal totalDep, Map<String, BigDecimal> stats, List<Defi> activeDefis, List<AiRecommendation> recommendations) {
        for (Map.Entry<String, BigDecimal> entry : stats.entrySet()) {
            String cat = entry.getKey();
            BigDecimal amount = entry.getValue();
            boolean hasActiveDefi = activeDefis.stream().anyMatch(d -> d.getCategorieCible().equalsIgnoreCase(cat));

            if ("Alimentation".equalsIgnoreCase(cat) && amount.compareTo(new BigDecimal(500)) > 0) {
                recommendations.add(new AiRecommendation(
                    hasActiveDefi ? "Suivi Nourriture" : "Astuce : Food Prep",
                    "Volume de d\u00E9pense Alimentation : " + amount + ".",
                    "info",
                    "Alimentation",
                    "Saviez-vous que pr\u00E9parer vos repas (Meal Prep) peut vous faire \u00E9conomiser jusqu'\u00E0 200 DT par mois ?",
                    !hasActiveDefi,
                    "Alimentation"
                ));
            } else if ("Loisirs".equalsIgnoreCase(cat) && totalDep.compareTo(BigDecimal.ZERO) > 0) {
                 if (amount.compareTo(totalDep.multiply(new BigDecimal("0.3"))) > 0) {
                    recommendations.add(new AiRecommendation(
                        "D\u00E9pensier : Loisirs",
                        "Plus de 30% de votre budget d\u00E9di\u00E9 au plaisir.",
                        "info",
                        "Loisirs",
                        "Nous aimons tous nous amuser, mais attention \u00E0 ne pas sacrifier vos objectifs de long terme pour des plaisirs imm\u00E9diats.",
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
            recommendations.add(new AiRecommendation(
                "Audit des Abonnements",
                "Vous avez " + services + " " + user.getDevise() + " de frais r\u00E9currents.",
                "info",
                "Optimisation",
                "Les abonnements 'fant\u00F4mes' sont les pires ennemis de l'\u00E9pargne. Faites le tri dans vos services digitaux.",
                false,
                null
            ));
        }
    }

    private void checkEmergencyFund(User user, List<AiRecommendation> recommendations) {
        BigDecimal currentSolde = user.getSoldeTotal() != null ? user.getSoldeTotal() : BigDecimal.ZERO;
        if (currentSolde.compareTo(new BigDecimal(500)) < 0) {
             recommendations.add(new AiRecommendation(
                "Priorit\u00E9 : Fonds d'Urgence",
                "Solde de s\u00E9curit\u00E9 tr\u00E8s bas.",
                "danger",
                "\u00C9pargne",
                "Il est crucial de mettre de c\u00F4t\u00E9 au moins 1000 DT pour les impr\u00E9vus (sant\u00E9, r\u00E9parations).",
                false,
                null
            ));
        }
    }

    private void checkPositiveAdvice(User user, List<AiRecommendation> recommendations) {
        BigDecimal solde = user.getSoldeTotal() != null ? user.getSoldeTotal() : BigDecimal.ZERO;
        if (solde.compareTo(new BigDecimal(3000)) > 0) {
            recommendations.add(new AiRecommendation(
                "Cap des 3k Franchi",
                "Solde total d\u00E9passant les 3000 " + user.getDevise() + " !",
                "success",
                "Global",
                "C'est un excellent r\u00E9sultat. Vous avez d\u00E9sormais une base solide pour investir ou lancer un grand projet.",
                false,
                null
            ));
        }
    }

    private void checkSmartBudgetAllocation(User user, List<AiRecommendation> recommendations) {
        recommendations.add(new AiRecommendation(
            "Strat\u00E9gie 50/30/20",
            "Atteindre l'\u00E9quilibre parfait.",
            "success",
            "Education",
            "Rappel : 50% pour vos Besoins, 30% pour vos Envies, et 20% pour votre Futur (\u00C9pargne).",
            false,
            null
        ));
    }

    private void checkSavingsPotential(User user, List<AiRecommendation> recommendations) {
        LocalDateTime now = LocalDateTime.now();
        if (now.getDayOfMonth() > 20) {
             recommendations.add(new AiRecommendation(
                "Astuce Fin de Mois",
                "Pr\u00E9parez votre virement d'\u00E9pargne.",
                "info",
                "\u00C9pargne",
                "La meilleure fa\u00E7on d'\u00E9pargner est de se payer en premier, d\u00E8s la r\u00E9ception du salaire.",
                false,
                null
            ));
        }
    }

    private void checkDebtAdvice(User user, List<AiRecommendation> recommendations) {
        if (!detteService.getActiveDettes(user).isEmpty()) {
            recommendations.add(new AiRecommendation(
                "Libert\u00E9 Financi\u00E8re",
                "Rembourser vos dettes lib\u00E8re votre futur.",
                "info",
                "Dettes",
                "M\u00EAme de petits remboursements suppl\u00E9mentaires chaque mois peuvent r\u00E9duire drastiquement vos int\u00E9r\u00EAts.",
                false,
                null
            ));
        }
    }

    private void checkForecastAdvice(User user, List<AiRecommendation> recommendations) {
        aiForecastService.getForecasts(user).stream().findFirst().ifPresent(f -> {
             recommendations.add(new AiRecommendation(
                "Vision Future",
                "Projection IA bas\u00E9e sur vos habitudes.",
                "info",
                "Pr\u00E9visions",
                "L'IA anticipe vos flux financiers. Consultez l'onglet Pr\u00E9visions pour une vue d\u00E9taill\u00E9e.",
                false,
                null
            ));
        });
    }

    private void checkSalaryAllocation(User user, List<AiRecommendation> recommendations) {
        // Just a constant tip to keep the screen varied
        recommendations.add(new AiRecommendation(
            "Le Saviez-Vous ?",
            "L'\u00E9pargne est un muscle qui se travaille.",
            "success",
            "Education",
            "Plus vous enregistrez vos transactions, plus mon analyse devient pr\u00E9cise et utile !",
            false,
            null
        ));
    }

    private void checkChallengeIncentive(User user, List<Defi> activeDefis, Map<String, BigDecimal> stats, List<AiRecommendation> recommendations) {
        if (activeDefis.isEmpty()) {
             recommendations.add(new AiRecommendation(
                "Pr\u00EAt pour un D\u00E9fi ?",
                "Boostez votre \u00E9pargne avec un challenge.",
                "info",
                "D\u00E9fis",
                "Les d\u00E9fis sont le meilleur moyen de changer vos habitudes de consommation de mani\u00E8re ludique.",
                true,
                "Global"
            ));
        }
    }
}
