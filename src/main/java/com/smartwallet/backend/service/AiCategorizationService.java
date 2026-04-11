package com.smartwallet.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwallet.backend.model.Categorie;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.repository.CategorieRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class AiCategorizationService {

    private final CategorieRepository categorieRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public AiCategorizationService(CategorieRepository categorieRepository) {
        this.categorieRepository = categorieRepository;
    }

    public Categorie categorize(String description, String type, User user) {
        System.out.println(">>> [AiCategorization] Categorizing: " + description + " (type: " + type + ")");
        
        String desc = description.toLowerCase();

        // --- GESTION DES REVENUS ---
        if ("REVENU".equalsIgnoreCase(type) || matches(desc, "salaire", "revenu", "gain", "reçu", "virement")) {
            return findOrCreateCategory("Salaire & Revenus", user, "REVENU");
        }

        // --- ALIMENTATION & RESTAURATION ---
        if (matches(desc, "monoprix", "carrefour", "mg", "restau", "manger", "viande", "poulet", "boucherie", "lait", "pain", "boulangerie", "pizza", "café", "coffee", "dîner", "déjeuner", "alimentation", "épicerie", "fruits", "légumes", "kfc", "mac", "food")) {
            return findOrCreateCategory("Alimentation", user, "DEPENSE");
        }

        // --- TRANSPORT ---
        if (matches(desc, "taxi", "bolt", "essence", "car", "gasoil", "parking", "autoroute", "peage", "bus", "train", "vitesse", "lavage", "mécanicien", "pneu", "transport")) {
            return findOrCreateCategory("Transport", user, "DEPENSE");
        }

        // --- SHOPPING & VETEMENTS ---
        if (matches(desc, "chaussure", "joliesse", "habit", "vêtement", "shopping", "zara", "pull", "chemise", "boutique", "mall", "geant", "azur", "pantalon", "robe", "sac", "bijou", "bague", "collier")) {
            return findOrCreateCategory("Shopping", user, "DEPENSE");
        }

        // --- SANTÉ ---
        if (matches(desc, "pharma", "doc", "clinique", "médicament", "dentiste", "santé", "hôpital", "analyse", "soin")) {
            return findOrCreateCategory("Santé", user, "DEPENSE");
        }

        // --- FACTURES & MAISON ---
        if (matches(desc, "steg", "sonede", "loyer", "internet", "telecom", "ooredoo", "orange", "topnet", "électricité", "gaz", "eau", "facture", "foyer", "meuble")) {
            return findOrCreateCategory("Logement & Factures", user, "DEPENSE");
        }

        // --- LOISIRS & DIVERS ---
        if (matches(desc, "cinéma", "netflix", "cadeau", "sport", "salle", "club", "vacances", "voyage", "hôtel", "abonnement")) {
            return findOrCreateCategory("Loisirs", user, "DEPENSE");
        }

        // 2. AI Call
        try {
            List<Categorie> existingCategories = categorieRepository.findByUser(user);
            String catName = callLLMToCategorize(description, type, existingCategories);
            if (catName != null && !catName.isEmpty()) {
                return findOrCreateCategory(catName, user, type);
            }
        } catch (Exception e) {}
        
        return findOrCreateCategory("Autre", user, type);
    }

    private boolean matches(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    private String getCleanKey() {
        return (geminiApiKey != null) ? geminiApiKey.trim().split(" ")[0] : "";
    }

    private String callLLMToCategorize(String description, String type, List<Categorie> existing) {
        try {
            String prompt = "Catégorise cette transaction (" + type + ") : \"" + description + "\". " +
                           "Choisis PARMI : " + existing.stream().map(Categorie::getNom).toList() + " ou une nouvelle catégorie simple. " +
                           "Réponds UNIQUEMENT le NOM de la catégorie.";

            Map<String, Object> body = Map.of(
                "contents", new Object[]{Map.of("parts", new Object[]{Map.of("text", prompt)})}
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash-latest:generateContent?key=" + getCleanKey()))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText().trim();
            }
        } catch (Exception e) {}
        return null;
    }

    private Categorie findOrCreateCategory(String nom, User user, String type) {
        return categorieRepository.findByUser(user).stream()
                .filter(c -> c.getNom().equalsIgnoreCase(nom))
                .findFirst()
                .orElseGet(() -> {
                    Categorie newCat = new Categorie(Character.toUpperCase(nom.charAt(0)) + nom.substring(1).toLowerCase());
                    newCat.setUser(user);
                    newCat.setType(type != null ? type.toUpperCase() : "DEPENSE");
                    return categorieRepository.save(newCat);
                });
    }
}