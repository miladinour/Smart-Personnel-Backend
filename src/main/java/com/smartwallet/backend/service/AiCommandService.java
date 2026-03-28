package com.smartwallet.backend.service;

import com.smartwallet.backend.dto.AiCommandResponse;
import com.smartwallet.backend.model.Categorie;
import com.smartwallet.backend.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiCommandService {

    private final AiCategorizationService aiCategorizationService;

    public AiCommandResponse processCommand(String command, String currentDevise, User user) {
        String cmdLower = command.toLowerCase();
        
        // 1. Détecter l'ajout de transaction
        if (cmdLower.matches(".*(ajoute|ajouter|ajouté|dépense|dépensé|acheté|achat|payé|payer|paye|reçu|gagné|salaire|revenu).*")) {
            return handleAddTransaction(cmdLower, currentDevise, user);
        }
        
        // 2. Détecter la navigation vers les stats
        if (cmdLower.contains("statistique") || cmdLower.contains("graphique") || cmdLower.contains("stats")) {
            return new AiCommandResponse("NAVIGATE", Map.of("screen", "STATISTICS"), "Voici vos statistiques.");
        }

        // 3. Réponse par défaut
        return new AiCommandResponse("TALK", Map.of("message", "Je n'ai pas bien compris votre commande. Essayez 'Ajouter 50 DT pour le taxi'."), "Besoin d'aide ?");
    }

    private AiCommandResponse handleAddTransaction(String cmd, String devise, User user) {
        double amount = extractAmount(cmd);
        boolean isRevenue = cmd.contains("reçu") || cmd.contains("revenu") || cmd.contains("salaire") || cmd.contains("gagné");
        
        String description = cleanDescription(cmd);
        Categorie category = aiCategorizationService.categorize(description, isRevenue ? "REVENU" : "DEPENSE", user);

        Map<String, Object> params = new HashMap<>();
        params.put("amount", amount);
        params.put("type", isRevenue ? "REVENU" : "DEPENSE");
        params.put("description", description);
        params.put("category", category != null ? category.getNom() : "Autre");

        String msg = String.format("D'accord, j'ai préparé l'ajout de %s %.2f pour '%s'.", 
            isRevenue ? "un revenu de" : "une dépense de", amount, description);

        return new AiCommandResponse("ADD_TRANSACTION", params, msg);
    }

    private double extractAmount(String cmd) {
        Pattern pattern = Pattern.compile("(\\d+([.,]\\d+)?)");
        Matcher matcher = pattern.matcher(cmd);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1).replace(',', '.'));
        }
        return 0.0;
    }

    private String cleanDescription(String cmd) {
        // Supprime les mots de commande courants pour garder la description
        String clean = cmd.replaceAll("(?i)(ajouter|ajoute|ajouté|une|un|dépense|dépenser|dépensé|acheté|achat|revenu|reçu|gagné|salaire|payé|payer|paye|au|aux|à|dans|pour|le|la|les|de|des|du|dt|dinar|dinars|euros|\\d+([.,]\\d+)?)", "").trim();
        return clean.isEmpty() ? "Dépense/Revenu Rapide" : clean;
    }
}
