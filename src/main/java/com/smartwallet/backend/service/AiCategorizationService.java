package com.smartwallet.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwallet.backend.model.Categorie;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.repository.CategorieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiCategorizationService {

    private final CategorieRepository categorieRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key:YOUR_API_KEY_HERE}")
    private String geminiApiKey;

    private static final Map<String, String[]> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("Alimentation", new String[] { "restau", "burger", "pizza", "carrefour", "monoprix", "magasin",
                "food", "eat", "cafe", "nourriture", "courses", "fastfood","restaurant","chocolat","gateau","pain",
                "boulangerie","patisserie","supermarche","superette","glace","soda","jus","eau","lait","yaourt",
                "fromage","viande","poisson","fruit","legume","snack","snack bar"});
        KEYWORDS.put("Transport", new String[] { "uber", "bolt", "taxi", "essence", "car", "train", "bus", "parking",
                "carburant", "vol","voiture","peage"});
        KEYWORDS.put("Santé", new String[] { "pharmacie", "docteur", "hosto", "medecin", "dentiste", "clinique", "soin" });
    }

    private String getCleanKey() {
        return (geminiApiKey != null) ? geminiApiKey.trim().split(" ")[0] : "";
    }

    public Categorie categorize(String description, String type, User user) {
        if (description == null || description.isEmpty()) {
            return getOrCreateOtherCategory(user, type);
        }

        String descLower = description.toLowerCase();

        // 1. MOTS-CLÉS (Plus performant pour les cas évidents)
        for (Map.Entry<String, String[]> entry : KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (descLower.contains(keyword.toLowerCase())) {
                    return getOrCreateCategory(entry.getKey(), type, user);
                }
            }
        }

        // 2. LLM PROFESSIONNEL (Gemini 2.5)
        if (getCleanKey().length() > 10) {
            // Récupérer les catégories existantes pour donner du contexte au LLM
            List<String> existingCategories = categorieRepository.findByUserAndType(user, type)
                    .stream().map(Categorie::getNom).toList();
            
            System.out.println("--- Analyse LLM pour : " + description + " ---");
            String result = callLLMToCategorize(description, type, existingCategories);
            
            if (result != null) {
                try {
                    JsonNode jsonResponse = objectMapper.readTree(result);
                    String categoryName = jsonResponse.path("category").asText();
                    double confidence = jsonResponse.path("confidence").asDouble();
                    
                    if (!categoryName.isBlank() && confidence > 0.4) {
                        System.out.println("LLM SUCCESS: " + categoryName + " (Conf: " + confidence + ")");
                        return getOrCreateCategory(categoryName, type, user);
                    }
                } catch (Exception e) {
                    System.err.println("Erreur parsing LLM: " + e.getMessage());
                    // Fallback sur le texte brut si le JSON échoue mais qu'on a un mot
                    if (!result.contains("{") && result.length() < 20) {
                         return getOrCreateCategory(result.trim(), type, user);
                    }
                }
            }
        }

        return getOrCreateOtherCategory(user, type);
    }

    private String callLLMToCategorize(String description, String type, List<String> existingCategories) {
        String[] modelsToTry = { "gemini-2.5-flash", "gemini-flash-latest" };
        String categoriesList = String.join(", ", existingCategories);

        String prompt = String.format(
            "Tu es un expert en comptabilité personnelle. Analyse cette transaction : '%s' (%s).\n" +
            "Catégories existantes : [%s].\n" +
            "Règles :\n" +
            "1. Choisis la catégorie la plus proche parmi les existantes si possible.\n" +
            "2. Si aucune ne correspond vraiment, suggère un nouveau nom court et précis (ex: 'Loisirs', 'Cadeaux').\n" +
            "3. RÉPONDS UNIQUEMENT AU FORMAT JSON : {\"category\": \"Nom\", \"confidence\": 0.0-1.0, \"reasoning\": \"...\"}.",
            description, type, categoriesList.isEmpty() ? "Aucune" : categoriesList
        );

        for (String model : modelsToTry) {
            try {
                Map<String, Object> body = Map.of(
                    "contents", new Object[]{
                        Map.of("parts", new Object[]{
                            Map.of("text", prompt)
                        })
                    },
                    "generationConfig", Map.of(
                        "response_mime_type", "application/json"
                    )
                );

                String jsonBody = objectMapper.writeValueAsString(body);
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + getCleanKey()))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(8))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode candidates = root.path("candidates");
                    if (candidates.isArray() && !candidates.isEmpty()) {
                        String result = candidates.get(0)
                                .path("content")
                                .path("parts")
                                .path(0)
                                .path("text")
                                .asText();
                        if (!result.isBlank()) {
                            return result.trim();
                        }
                    }
                    System.err.println("Gemini : Réponse vide ou format inattendu - " + response.body());
                } else {
                    System.err.println("Erreur API Gemini [" + model + "] : " + response.statusCode() + " - " + response.body());
                }
            } catch (Exception e) {
                System.err.println("Exception lors de l'appel LLM " + model + " : " + e.getMessage());
            }
        }
        return null;
    }

    private Categorie getOrCreateCategory(String nom, String type, User user) {
        String cleanNom = nom.trim();
        if (cleanNom.length() > 50) cleanNom = cleanNom.substring(0, 50);
        
        // Capitaliser la première lettre
        if (!cleanNom.isEmpty()) {
            cleanNom = cleanNom.substring(0, 1).toUpperCase() + cleanNom.substring(1);
        }

        Optional<Categorie> existing = categorieRepository.findByNomAndUser(cleanNom, user);
        if (existing.isPresent()) {
            return existing.get();
        }

        Categorie newCat = new Categorie();
        newCat.setNom(cleanNom);
        newCat.setType(type);
        newCat.setUser(user);
        return categorieRepository.save(newCat);
    }

    private Categorie getOrCreateOtherCategory(User user, String type) {
        return getOrCreateCategory("Autre", type, user);
    }
}
