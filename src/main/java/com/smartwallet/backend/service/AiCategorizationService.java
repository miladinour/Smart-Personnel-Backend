package com.smartwallet.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwallet.backend.model.Categorie;
import com.smartwallet.backend.model.Transaction;
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
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

@Service
public class AiCategorizationService {

    private final CategorieRepository categorieRepository;
    private final com.smartwallet.backend.repository.TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${ai.llm.provider:GEMINI}")
    private String llmProvider;

    @Value("${ollama.base.url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model.name:llama3}")
    private String ollamaModelName;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    public AiCategorizationService(CategorieRepository categorieRepository, com.smartwallet.backend.repository.TransactionRepository transactionRepository) {
        this.categorieRepository = categorieRepository;
        this.transactionRepository = transactionRepository;
    }

    public Categorie categorize(String text, String type, User user) {
        if (text == null || text.isBlank()) return findOrCreateCategory("Autre", user, type);
        
        System.out.println(">>> [AiCategorization] Processing cascade for: " + text);
        String desc = text.toLowerCase().trim();

        // --- NIVEAU 1 : MOTS-CLÉS (Instantané) ---
        Categorie fromKeywords = checkKeywords(desc, type, user);
        if (fromKeywords != null) {
            System.out.println(">>> [AiCategorization] Level 1 (Keywords) hit: " + fromKeywords.getNom());
            return fromKeywords;
        }

        // --- NIVEAU 2 : MÉMOIRE / HISTORIQUE EXACT (Local) ---
        try {
            // Match exact uniquement pour éviter les faux positifs (ex: Croquettes ne matchera plus Amende)
            Optional<Transaction> lastTx = transactionRepository.findTopByDescriptionIgnoreCaseAndUserOrderByDateDesc(text, user);
            if (lastTx.isPresent()) {
                Categorie memoCat = lastTx.get().getCategorie();
                if (!"Autre".equalsIgnoreCase(memoCat.getNom())) {
                    System.out.println(">>> [AiCategorization] Level 2 (Memory Exact) hit: " + memoCat.getNom());
                    return memoCat;
                }
            }
        } catch (Exception e) {
            System.err.println(">>> [AiCategorization] Level 2 Error: " + e.getMessage());
        }

        List<Categorie> allCats = categorieRepository.findByUser(user);

        // --- NIVEAU 3 : SIMILARITÉ MATHÉMATIQUE STRICTE (Dice) - Local ---
        Categorie similar = findBestMatchingCategory(text, allCats);
        if (similar != null && !"Autre".equalsIgnoreCase(similar.getNom())) {
            System.out.println(">>> [AiCategorization] Level 3 (Similarity 0.85) hit: " + similar.getNom());
            return similar;
        }

        // --- NIVEAU 4 : IA LOCALE (Ollama) - Local ---
        if ("OLLAMA".equalsIgnoreCase(llmProvider)) {
            System.out.println(">>> [AiCategorization] Attempting Level 4 (Ollama)...");
            String catName = callOllama(text, type, allCats);
            if (catName != null && !catName.isBlank() && !"Autre".equalsIgnoreCase(catName)) {
                System.out.println(">>> [AiCategorization] Level 4 (Ollama) SUCCESS: " + catName);
                return findOrCreateCategory(catName, user, type);
            }
            System.out.println(">>> [AiCategorization] Level 4 (Ollama) failed or returned 'Autre'");
        }

        // --- FALLBACK FACULTATIF : GEMINI (Cloud) ---
        if (geminiApiKey != null && !geminiApiKey.isBlank() && !"OLLAMA".equalsIgnoreCase(llmProvider)) {
            System.out.println(">>> [AiCategorization] Attempting Fallback (Gemini)...");
            String catName = callGemini(text, type, allCats);
            if (catName != null && !catName.isBlank()) return findOrCreateCategory(catName, user, type);
        }

        System.out.println(">>> [AiCategorization] All levels failed. Defaulting to 'Autre'");
        return findOrCreateCategory("Autre", user, type);
    }

    private Categorie checkKeywords(String desc, String type, User user) {
        if ("REVENU".equalsIgnoreCase(type) || matches(desc, "salaire", "revenu", "gain", "reçu", "virement", "bonus", "intérêt")) {
            return findOrCreateCategory("Salaire", user, "REVENU");
        }
        if (matches(desc, "monoprix", "carrefour", "mg", "restau", "manger", "viande", "boulangerie", "lait", "pain", "pizza", "café", "alimentation", "épicerie", "fruits", "légumes", "kfc", "mac", "food", "lidl", "supermarché", "monoprix")) {
            return findOrCreateCategory("Alimentation", user, "DEPENSE");
        }
        if (matches(desc, "taxi", "bolt", "essence", "carburant", "gasoil", "parking", "peage", "bus", "train", "transport", "voiture")) {
            return findOrCreateCategory("Transport", user, "DEPENSE");
        }
        if (matches(desc, "chaussure", "habit", "vêtement", "shopping", "pull", "chemise", "boutique", "mode", "beauté", "cosmétique", "coiffeur", "barbier", "salon", "basket", "paire", "sneakers", "talon", "marque")) {
            return findOrCreateCategory("Shopping", user, "DEPENSE");
        }
        if (matches(desc, "pharma", "médicament", "dentiste", "santé", "hôpital", "soin", "cardiologue", "ophtalmo", "clinique", "médecin", "docteur", "analyse")) {
            return findOrCreateCategory("Santé", user, "DEPENSE");
        }
        if (matches(desc, "steg", "sonede", "loyer", "internet", "telecom", "ooredoo", "orange", "topnet", "électricité", "facture", "eau", "steg", "gaz")) {
            return findOrCreateCategory("Logement & Factures", user, "DEPENSE");
        }
        if (matches(desc, "cinéma", "netflix", "spotify", "cadeau", "sport", "salle", "club", "vacances", "voyage", "abonnement", "loisirs")) {
            return findOrCreateCategory("Loisirs", user, "DEPENSE");
        }
        return null;
    }

    private Categorie findBestMatchingCategory(String text, List<Categorie> categories) {
        double bestScore = 0;
        Categorie bestMatch = null;
        for (Categorie cat : categories) {
            double score = diceCoefficient(text.toLowerCase(), cat.getNom().toLowerCase());
            if (score > 0.35 && score > bestScore) {
                bestScore = score;
                bestMatch = cat;
            }
        }
        return bestMatch;
    }

    private double diceCoefficient(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        if (s1.length() < 2 || s2.length() < 2) return 0;
        java.util.Set<String> bigrams1 = new java.util.HashSet<>();
        for (int i = 0; i < s1.length() - 1; i++) bigrams1.add(s1.substring(i, i + 2));
        java.util.Set<String> bigrams2 = new java.util.HashSet<>();
        for (int i = 0; i < s2.length() - 1; i++) bigrams2.add(s2.substring(i, i + 2));
        int intersection = 0;
        for (String b : bigrams1) if (bigrams2.contains(b)) intersection++;
        return (2.0 * intersection) / (bigrams1.size() + bigrams2.size());
    }

    private String callOllama(String text, String type, List<Categorie> existing) {
        try {
            // Filtrer la liste pour ne pas polluer l'IA avec d'anciens tests ratés (noms trop longs)
            String cats = String.join(",", existing.stream()
                    .map(Categorie::getNom)
                    .filter(name -> !"Autre".equalsIgnoreCase(name) && name.length() <= 20)
                    .limit(40)
                    .toList());
            
            System.out.println(">>> [Ollama] Pro mode - Analyzing '" + text + "'");
            
            // Forcer l'IA à répondre en UN SEUL MOT et en Français
            String prompt = "Classify this expense in French: \"" + text + "\"\n" +
                           "Options: " + cats + "\n" +
                           "Answer with ONE SINGLE WORD (the best category).";

            Map<String, Object> options = Map.of(
                "temperature", 0.0,
                "num_predict", 5,      // Très court : juste un mot
                "num_ctx",    2048,
                "top_k",      1
            );

            Map<String, Object> body = Map.of(
                "model",   ollamaModelName,
                "prompt",  prompt,
                "stream",  false,
                "options", options
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(45)) // Timeout augmenté à 45s par sécurité
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(">>> [Ollama] Status: " + response.statusCode());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String res = root.path("response").asText().trim();
                System.out.println(">>> [Ollama] Response: " + res);
                if (res.contains("\n")) res = res.substring(0, res.indexOf("\n")).trim();
                return res.replaceAll("[^a-zA-Z\u00C0-\u024F &]", "").trim();
            }
        } catch (Exception e) {
            System.err.println(">>> [AiCategorization] Ollama failed: " + e.getMessage());
        }
        return null;
    }
    private String callGemini(String text, String type, List<Categorie> existing) {
        try {
            String existingList = String.join(", ", existing.stream().map(Categorie::getNom).toList());
            String prompt = "Catégorise cette transaction (" + type + ") : \"" + text + "\".\n" +
                           "RÈGLES : Réponds uniquement le nom de la catégorie choisie parmi [" + existingList + "] ou une nouvelle catégorie courte.";

            Map<String, Object> body = Map.of("contents", new Object[]{Map.of("parts", new Object[]{Map.of("text", prompt)})});
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash-latest:generateContent?key=" + getCleanKey()))
                    .header("Content-Type", "application/json")
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

    private boolean matches(String text, String... keywords) {
        String lowerText = text.toLowerCase().trim();
        for (String k : keywords) {
            String lowerK = k.toLowerCase();
            // 1. Recherche avec frontières de mots pour éviter 'eau' dans 'bureau'
            if (lowerText.matches(".*\\b" + Pattern.quote(lowerK) + "\\b.*")) {
                System.out.println(">>> [Keywords] Exact word match found: '" + lowerK + "' in '" + lowerText + "'");
                return true;
            }
            
            // 2. Recherche par similarité STRICTE (pour éviter les faux positifs)
            if (diceCoefficient(lowerText, lowerK) > 0.85) { // Seuil augmenté de 0.8 à 0.85
                System.out.println(">>> [Keywords] Strict similarity match: '" + lowerK + "' with '" + lowerText + "'");
                return true;
            }
        }
        return false;
    }

    private String getCleanKey() {
        if (geminiApiKey == null || geminiApiKey.isBlank()) return "";
        return geminiApiKey.trim();
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