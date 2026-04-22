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
                    "Oups, Solde N\u00E9gatif",
                    "Ce mois-ci, vos d\u00E9penses d\u00E9passent vos revenus.",
                    "danger",
                    "Global",
                    "Votre balance est actuellement n\u00E9gative. Pas de panique ! Essayez de limiter les achats 'plaisir' sur les prochains jours pour r\u00E9tablir l'\u00E9quilibre.",
                    false,
                    null
                ));
            } else if (totalRevenus.compareTo(BigDecimal.ZERO) > 0) {
                recommendations.add(new AiRecommendation(
                    "Balance Positive !",
                    "F\u00E9licitations, vous g\u00E9rez parfaitement votre budget.",
                    "success",
                    "Global",
                    "C'est la base d'une bonne sant\u00E9 financi\u00E8re. Vous pourriez allouer une partie de ce surplus \u00E0 l'un de vos objectifs d'\u00E9pargne.",
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
                        "Budget de " + catName + " Atteint",
                        "Vous avez atteint votre limite de " + limit + " pour cette cat\u00E9gorie.",
                        "danger",
                        catName,
                        hasActiveDefi ? "Budget atteint, mais votre d\u00E9fi est l\u00E0 pour vous aider. Gardez le cap !" : "Pour ne pas impacter vos autres besoins, essayez de freiner vos d\u00E9penses en " + catName + " jusqu'\u00E0 la fin du mois.",
                        !hasActiveDefi,
                        catName
                    ));
                } else if (spent.compareTo(limit.multiply(new BigDecimal("0.8"))) >= 0) {
                    recommendations.add(new AiRecommendation(
                        "Attention au Budget " + catName,
                        "Vous avez d\u00E9j\u00E0 consomm\u00E9 80% de votre budget.",
                        "warning",
                        catName,
                        "Il vous reste encore quelques jours avant la fin du mois. Soyez vigilant pour rester dans le vert !",
                        false,
                        null
                    ));
                }
            });

        // 3. Trends & Habit-based Advice (Only if NOT new)
        if (!isNewUser) {
            checkTrendAdvice(user, now, recommendations);
            checkCategoryAdvice(user, totalDepenses, stats, activeDefis, recommendations);
            checkForecastAdvice(user, recommendations);
            checkSalaryAllocation(user, recommendations); // Le Saviez-Vous ?
            checkChallengeIncentive(user, activeDefis, stats, recommendations); // Pr\u00EAt pour un D\u00E9fi ?
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

    private void checkWelcomeAdvice(User user, List<AiRecommendation> recommendations) {
        recommendations.add(new AiRecommendation(
            "Bienvenue sur Smart Wallet !",
            "Pr\u00EAt \u00E0 reprendre le contr\u00F4le de vos finances ?",
            "success",
            "Onboarding",
            "Commencez par ajouter votre premier revenu ou une d\u00E9pense r\u00E9cente. Plus vous ajoutez de donn\u00E9es, plus mes conseils seront pr\u00E9cis !",
            false,
            null
        ));
        
        recommendations.add(new AiRecommendation(
            "Premi\u00E8re \u00C9tape : Le Budget",
            "Fixez-vous des limites pour mieux \u00E9conomiser.",
            "info",
            "Education",
            "Cr\u00E9ez un budget pour vos cat\u00E9gories principales (Alimentation, Loisirs). Je vous pr\u00E9viendrai d\u00E8s que vous approcherez de vos limites.",
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
                recommendations.add(new AiRecommendation(
                    hasActiveDefi ? "Suivi Alimentation" : "Astuce : Le 'Meal Prep'",
                    "Votre budget nourriture est important ce mois-ci (" + amount + ").",
                    "info",
                    "Alimentation",
                    "Saviez-vous que pr\u00E9parer vos repas \u00E0 l'avance peut vous faire \u00E9conomiser jusqu'\u00E0 200 " + user.getDevise() + " par mois ? Essayez de cuisiner un peu plus !",
                    !hasActiveDefi,
                    "Alimentation"
                ));
            } else if ("Loisirs".equalsIgnoreCase(cat) && totalDep.compareTo(BigDecimal.ZERO) > 0) {
                 if (amount.compareTo(totalDep.multiply(new BigDecimal("0.3"))) > 0) {
                    recommendations.add(new AiRecommendation(
                        "Focus : Loisirs & Plaisirs",
                        "Les sorties et loisirs repr\u00E9sentent plus de 30% de vos d\u00E9penses.",
                        "info",
                        "Loisirs",
                        "Se faire plaisir est essentiel, mais attention \u00E0 ce que cela ne retarde pas vos projets importants sur le long terme.",
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

    private void checkEmergencyFund(User user, boolean isNewUser, List<AiRecommendation> recommendations) {
        BigDecimal currentSolde = user.getSoldeTotal() != null ? user.getSoldeTotal() : BigDecimal.ZERO;
        if (currentSolde.compareTo(new BigDecimal(500)) < 0) {
             recommendations.add(new AiRecommendation(
                isNewUser ? "Objectif : Fonds d'Urgence" : "Priorit\u00E9 : Fonds d'Urgence",
                isNewUser ? "La base de la s\u00E9curit\u00E9 financi\u00E8re." : "Solde de s\u00E9curit\u00E9 tr\u00E8s bas.",
                isNewUser ? "info" : "danger",
                "\u00C9pargne",
                "Il est conseill\u00E9 de mettre de c\u00F4t\u00E9 au moins 1000 DT pour parer aux impr\u00E9vus sans toucher \u00E0 vos revenus courants.",
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
            "La R\u00E8gle d'Or 50/30/20",
            "Comment r\u00E9partir intelligemment votre argent.",
            "success",
            "Education",
            "La m\u00E9thode id\u00E9ale : 50% pour vos Besoins (loyer, factures), 30% pour vos Plaisirs, et 20% pour votre Futur (\u00C9pargne). Essayez de vous en rapprocher !",
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
            "L'\u00E9pargne est une habitude qui se construit petit \u00E0 petit, comme un muscle que l'on entra\u00EEne.",
            "success",
            "Education",
            "La cl\u00E9 n'est pas le montant, mais la r\u00E9gularit\u00E9. En enregistrant chaque petite transaction, vous musclez votre discipline financi\u00E8re et me permettez d'affiner mes conseils.",
            false,
            null
        ));
    }

    private void checkChallengeIncentive(User user, List<Defi> activeDefis, Map<String, BigDecimal> stats, List<AiRecommendation> recommendations) {
        if (activeDefis.isEmpty()) {
             recommendations.add(new AiRecommendation(
                "Pr\u00EAt pour un D\u00E9fi ?",
                "Lancez un mini-challenge de 7 jours pour r\u00E9duire vos d\u00E9penses sans effort.",
                "info",
                "D\u00E9fis",
                "Les d\u00E9fis vous aident \u00E0 identifier vos d\u00E9penses superflues de mani\u00E8re ludique. C'est le meilleur moyen de booster votre \u00E9pargne ce mois-ci !",
                true,
                "Global"
            ));
        }
    }
}
