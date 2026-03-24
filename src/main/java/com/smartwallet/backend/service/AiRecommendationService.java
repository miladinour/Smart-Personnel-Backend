package com.smartwallet.backend.service;

import com.smartwallet.backend.dto.AiRecommendation;
import com.smartwallet.backend.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiRecommendationService {

    private final DepenseService depenseService;
    private final RevenuService revenuService;

    public List<AiRecommendation> getRecommendations(User user) {
        List<AiRecommendation> recommendations = new ArrayList<>();

        BigDecimal totalDepenses = depenseService.getTotalDepenses(user);
        BigDecimal totalRevenus = revenuService.getTotalRevenus(user);
        Map<String, BigDecimal> stats = depenseService.getDepensesByCategory(user);

        // 1. Check if spending > income
        if (totalDepenses.compareTo(totalRevenus) > 0 && totalRevenus.compareTo(BigDecimal.ZERO) > 0) {
            recommendations.add(new AiRecommendation(
                "Attention au Budget",
                "Vos dépenses dépassent vos revenus ce mois-ci. Essayez de réduire vos coûts non essentiels.",
                "warning",
                "Global"
            ));
        }

        // 2. Identify top category
        String topCategory = null;
        BigDecimal maxAmount = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : stats.entrySet()) {
            if (entry.getValue().compareTo(maxAmount) > 0) {
                maxAmount = entry.getValue();
                topCategory = entry.getKey();
            }
        }

        if (topCategory != null) {
            if ("Alimentation".equalsIgnoreCase(topCategory) && maxAmount.compareTo(new BigDecimal(500)) > 0) {
                recommendations.add(new AiRecommendation(
                    "Conseil Cuisine",
                    "Vous dépensez beaucoup en alimentation. Cuisiner à la maison pourrait vous faire économiser jusqu'à 30%.",
                    "info",
                    "Alimentation"
                ));
            } else if ("Loisirs".equalsIgnoreCase(topCategory)) {
                recommendations.add(new AiRecommendation(
                    "Gestion des Loisirs",
                    "Les loisirs sont votre premier poste de dépense. Fixez-vous une limite hebdomadaire pour mieux épargner.",
                    "info",
                    "Loisirs"
                ));
            } else {
                recommendations.add(new AiRecommendation(
                    "Analyse de Catégorie",
                    "La catégorie '" + topCategory + "' représente votre plus grande dépense. Vérifiez si tous ces achats étaient nécessaires.",
                    "info",
                    topCategory
                ));
            }
        }

        // 3. Savings advice
        if (totalRevenus.subtract(totalDepenses).compareTo(new BigDecimal(200)) > 0) {
            recommendations.add(new AiRecommendation(
                "Objectif Épargne",
                "Bravo ! Vous avez un surplus ce mois-ci. C'est le moment idéal pour alimenter votre fonds d'urgence.",
                "success",
                "Épargne"
            ));
        }

        // 4. Default if no data
        if (recommendations.isEmpty()) {
            recommendations.add(new AiRecommendation(
                "Bienvenue !",
                "Commencez à ajouter vos transactions pour recevoir des conseils personnalisés sur votre gestion financière.",
                "info",
                "Général"
            ));
        }

        return recommendations;
    }
}
