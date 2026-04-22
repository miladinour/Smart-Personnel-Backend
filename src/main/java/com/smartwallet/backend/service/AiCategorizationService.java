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
            System.out.println(">>> [DEBUG] Level 1 MATCH FOUND: " + fromKeywords.getNom() + " (ID: " + fromKeywords.getId() + ")");
            return fromKeywords;
        } else {
            System.out.println(">>> [DEBUG] Level 1 NO MATCH for: " + desc);
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

        List<Categorie> allCats = categorieRepository.findByUserOrUserIsNull(user);

        // --- NIVEAU 3 : SIMILARITÉ MATHÉMATIQUE STRICTE (Dice) - Local ---
        Categorie similar = findBestMatchingCategory(text, allCats);
        if (similar != null && !"Autre".equalsIgnoreCase(similar.getNom())) {
            System.out.println(">>> [AiCategorization] Level 3 (Similarity 0.85) hit: " + similar.getNom());
            return similar;
        }

        // --- NIVEAU 4 : IA LOCALE (Ollama) - Local ---
        String catName = null;
        if ("OLLAMA".equalsIgnoreCase(llmProvider)) {
            System.out.println(">>> [AiCategorization] Level 4 (Ollama) attempt...");
            catName = callOllama(text, type, allCats);
        }

        // --- NIVEAU 5 : IA CLOUD (Gemini) - Fallback si Ollama échoue ou est désactivé ---
        if ((catName == null || "Autre".equalsIgnoreCase(catName)) && geminiApiKey != null && !geminiApiKey.isBlank()) {
            System.out.println(">>> [AiCategorization] Level 5 (Gemini Fallback) attempt...");
            catName = callGemini(text, type, allCats);
        }

        if (catName != null && !catName.isBlank() && !"Autre".equalsIgnoreCase(catName)) {
            System.out.println(">>> [AiCategorization] AI Success: " + catName);
            return findOrCreateCategory(catName, user, type);
        }

        System.out.println(">>> [AiCategorization] All levels failed. Defaulting to 'Autre'");
        return findOrCreateCategory("Autre", user, type);
    }

    private Categorie checkKeywords(String desc, String type, User user) {
        if (matches(desc, "cadeau", "fleur", "bouquet", "anniversaire", "fête", "don", "mariage", "naissance", "fleurs", "maman", "mama")) {
            return findOrCreateCategory("Cadeau", user, "DEPENSE");
        }
        if (matches(desc, "monoprix", "carrefour", "mg", "restau", "manger", "viande", "boulangerie", "lait", "pain", "pizza", "café", "alimentation", "épicerie", "fruits", "légumes", "kfc", "mac", "food", "lidl", "supermarché", "monoprix")) {
            return findOrCreateCategory("Alimentation", user, "DEPENSE");
        }
        if (matches(desc, "taxi", "bolt", "essence", "carburant", "gasoil", "sans plomb", "95", "98", "diesel", "parking", "peage", "bus", "train", "transport", "voiture")) {
            return findOrCreateCategory("Transport", user, "DEPENSE");
        }
        if (matches(desc, "chaussure", "habit", "vêtement", "shopping", "pull", "chemise", "boutique", "mode", "basket", "paire", "sneakers", "talon", "marque")) {
            return findOrCreateCategory("Shopping", user, "DEPENSE");
        }
        if (matches(desc, "pharma", "médicament", "dentiste", "santé", "hôpital", "soin", "cardiologue", "ophtalmo", "clinique", "médecin", "docteur", "analyse")) {
            return findOrCreateCategory("Santé", user, "DEPENSE");
        }
        if (matches(desc, "steg", "sonede", "loyer", "internet", "telecom", "ooredoo", "orange", "topnet", "électricité", "facture", "eau", "steg", "gaz", "menuiserie", "bricolage", "meuble", "réparation")) {
            return findOrCreateCategory("Logement", user, "DEPENSE");
        }
        if (matches(desc, "cinéma", "netflix", "spotify", "cadeau", "sport", "salle", "club", "vacances", "voyage", "abonnement", "loisirs", "disney", "prime")) {
            return findOrCreateCategory("Loisirs", user, "DEPENSE");
        }
        if (matches(desc, "coiffeur", "barbier", "beauté", "cosmétique", "soin", "esthétique")) {
            return findOrCreateCategory("Beauté", user, "DEPENSE");
        }
        if (matches(desc, "croquettes", "chat", "chien", "vétérinaire", "animalerie")) {
            return findOrCreateCategory("Animaux", user, "DEPENSE");
        }
        if (matches(desc, "amende", "radar", "pénalité", "pv")) {
            return findOrCreateCategory("Amende", user, "DEPENSE");
        }
        return null;
    }

    private Categorie findBestMatchingCategory(String text, List<Categorie> categories) {
        double bestScore = 0;
        Categorie bestMatch = null;
        for (Categorie cat : categories) {
            double score = diceCoefficient(text.toLowerCase(), cat.getNom().toLowerCase());
            if (score > 0.70 && score > bestScore) { // Seuil augmenté de 0.35 à 0.70 pour plus de précision
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
            
            // Format de prompt "Pédagogique" pour Phi-3
            String prompt = "Tu es un assistant financier. Classe cette dépense : '" + text + "'.\n" +
                           "Options : " + cats + ", Autre\n\n" +
                           "RÈGLES :\n" +
                           "1. Choisis la catégorie la plus LOGIQUE.\n" +
                           "2. Si le texte est du charabia ou n'a aucun sens comme 'truc', 'bidule' ou 'azerty', réponds UNIQUEMENT 'Autre'.\n" +
                           "3. Réponds par UN SEUL MOT.\n\n" +
                           "Réponse :";

            Map<String, Object> options = Map.of(
                "temperature", 0.0,
                "num_predict", 8,      
                "num_ctx",    1024,
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
                System.out.println(">>> [Ollama] Raw Response: " + res);
                
                // Nettoyage agressif : on prend le premier mot
                if (res.contains(" ")) res = res.split(" ")[0];
                if (res.contains("\n")) res = res.split("\n")[0];
                res = res.replaceAll("[^a-zA-Z\u00C0-\u024F]", "").trim();
                
                // LOGIQUE PRO : Avant de créer une nouvelle catégorie, on cherche si un match existe déjà 
                // même si l'IA a fait une petite faute de frappe ou a répondu différemment.
                for (Categorie cat : existing) {
                    if (diceCoefficient(res.toLowerCase(), cat.getNom().toLowerCase()) > 0.7) {
                        return cat.getNom();
                    }
                }
                
                return res;
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
        if (text == null || text.isBlank()) return false;
        String lowerText = text.toLowerCase().trim();

        for (String k : keywords) {
            String lowerK = k.toLowerCase().trim();
            
            boolean matched = false;
            if (lowerK.length() <= 4) {
                if (lowerText.matches(".*\\b" + Pattern.quote(lowerK) + "\\b.*")) {
                    if (!lowerText.equals("achat") && !lowerText.equals("le") && !lowerText.equals("un")) {
                        matched = true;
                    }
                }
            } else {
                if (lowerText.contains(lowerK)) matched = true;
                else if (diceCoefficient(lowerText, lowerK) > 0.80) matched = true;
            }

            if (matched) {
                System.out.println(">>> [DEBUG] KEYWORD MATCH: '" + lowerK + "' found in '" + lowerText + "'");
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
        // On cherche d'abord dans les catégories de l'utilisateur ET les catégories système (user is null)
        return categorieRepository.findByUserOrUserIsNull(user).stream()
                .filter(c -> c.getNom().trim().equalsIgnoreCase(nom.trim()))
                .findFirst()
                .orElseGet(() -> {
                    String cleanNom = nom.trim();
                    String capitalizedNom = Character.toUpperCase(cleanNom.charAt(0)) + cleanNom.substring(1).toLowerCase();
                    System.out.println(">>> [AiCategorization] Creating NEW category for user: " + capitalizedNom);
                    Categorie newCat = new Categorie();
                    newCat.setNom(capitalizedNom);
                    newCat.setUser(user);
                    newCat.setType(type != null ? type.toUpperCase() : "DEPENSE");
                    newCat.setSystemCategory(false);
                    return categorieRepository.save(newCat);
                });
    }
}