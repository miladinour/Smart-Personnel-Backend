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
        
        // 1. Détecter l'ajout de transaction (Mode Unicode pour les accents)
        if (cmdLower.matches("(?Ui).*\\b(ajoute|ajouter|ajouté|dépense|dépenser|dépensé|acheté|achat|payé|payer|paye|reçu|gagné|salaire|revenu)\\b.*")) {
            return handleAddTransaction(cmdLower, currentDevise, user);
        }
        
        // 2. Détecter la navigation vers les stats
        if (cmdLower.matches("(?Ui).*\\b(statistique|graphique|stats|statistiques|graphiques)\\b.*")) {
            return new AiCommandResponse("NAVIGATE", Map.of("screen", "STATISTICS"), "Voici vos statistiques.");
        }

        // 3. Réponse par défaut
        return new AiCommandResponse("TALK", Map.of("message", "Je n'ai pas bien compris votre commande. Essayez 'Ajouter 50 DT pour le taxi'."), "Besoin d'aide ?");
    }

    private AiCommandResponse handleAddTransaction(String cmd, String devise, User user) {
        double amount = extractAmount(cmd);
        // Détection de revenu par mot complet pour éviter les faux positifs
        boolean isRevenue = cmd.matches("(?Ui).*\\b(reçu|revenu|salaire|gagné)\\b.*");
        
        String description = cleanDescription(cmd);
        Categorie category = aiCategorizationService.categorize(description, isRevenue ? "REVENU" : "DEPENSE", user);
        String dateStr = extractDate(cmd);

        Map<String, Object> params = new HashMap<>();
        params.put("amount", amount);
        params.put("type", isRevenue ? "REVENU" : "DEPENSE");
        params.put("description", description);
        params.put("category", category != null ? category.getNom() : "Autre");
        params.put("date", dateStr);

        String msg = String.format("D'accord, ajout de %.2f pour '%s'.", amount, description);

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

    private String extractDate(String cmd) {
        // Détection de date par mot complet (?Ui)\b
        if (cmd.matches("(?Ui).*\\b(avant-hier|avant hier|il y a 2 jours)\\b.*")) {
            return java.time.LocalDate.now().minusDays(2).toString();
        } else if (cmd.matches("(?Ui).*\\b(hier|la veille)\\b.*")) {
            return java.time.LocalDate.now().minusDays(1).toString();
        } else if (cmd.matches("(?Ui).*\\b(demain)\\b.*")) {
            return java.time.LocalDate.now().plusDays(1).toString();
        }
        return java.time.LocalDate.now().toString(); // default (aujourd'hui)
    }

    private String cleanDescription(String cmd) {
        // 1. Dictionnaire de "bruit" étendu pour un rendu pro
        String noiseWordsRegex = "(?Ui)\\b(ajouter|ajoute|ajouté|une|un|le|la|les|des|du|de|d'un|d'une|une|un|dépense|dépenser|dépensé|acheté|achat|j'ai|je|mon|ma|mes|revenu|reçu|gagné|salaire|payé|payer|paye|au|aux|à|dans|en|pour|avec|et|dt|dinar|dinars|euros|hier|avant-hier|aujourd'hui|demain|visite|chez|rendez-vous|rdv|achat|payement|paiement|virement|transfert)\\b";
        
        // 2. Suppression des montants et de la ponctuation inutile
        String amountRegex = "\\d+([.,]\\d+)?";
        
        String clean = cmd.replaceAll(noiseWordsRegex, " ");
        clean = clean.replaceAll(amountRegex, " ");
        clean = clean.replaceAll("[.,!?;:]", " "); // Suppression ponctuation
        
        // 3. Nettoyage des espaces
        clean = clean.replaceAll("\\s+", " ").trim();
        
        if (clean.isEmpty()) return "Transaction";
        
        // 4. Limiter à 3 mots significatifs (plus pro pour un titre)
        String[] words = clean.split(" ");
        StringBuilder finalTitle = new StringBuilder();
        int max = Math.min(words.length, 3);
        for (int i = 0; i < max; i++) {
            finalTitle.append(words[i]).append(" ");
        }
        
        String result = finalTitle.toString().trim();
        
        // 5. Capitalisation de la première lettre
        if (result.length() > 1) {
            return result.substring(0, 1).toUpperCase() + result.substring(1).toLowerCase();
        }
        return result.toUpperCase();
    }
}
