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

        // --- NIVEAU 4 : IA LOCALE (Ollama) - Local First ---
        String catName = null;
        System.out.println(">>> [AiCategorization] Level 4 (Ollama) attempt...");
        catName = callOllama(text, type, allCats);

        // --- NIVEAU 5 : IA CLOUD (Gemini) - Fallback Cloud ---
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
        String d = desc.toLowerCase().trim();
        
        // --- PRIORITÉ 1 : MATCH EXACT AVEC LES NOMS DE CATÉGORIES DE L'USER ---
        List<Categorie> userCats = categorieRepository.findByUserOrUserIsNull(user);
        for (Categorie c : userCats) {
            if (c.getNom().toLowerCase().trim().equals(d)) return c;
        }

        // --- PRIORITÉ 2 : MOTS-CLÉS SPÉCIFIQUES ---
        if (matches(d, "cadeau", "cadeaux", "fleur", "bouquet", "anniversaire", "fête", "don", "mariage", "naissance", "fleurs", "maman", "mama", "surprise", "kdo")) {
            return findOrCreateCategory("Cadeau", user, "DEPENSE");
        }
        if (matches(d, "monoprix", "carrefour", "mg", "restau", "manger", "viande", "boulangerie", "lait", "pain", "pizza", "café", "alimentation", "épicerie", "fruits", "légumes", "kfc", "mac", "food", "lidl", "supermarché", "resto", "diner", "déjeuner", "snack", "sandwich", "gâteau", "gateau", "pâtisserie", "chocolat", "eau mineral", "oeufs", "poulet")) {
            return findOrCreateCategory("Alimentation", user, "DEPENSE");
        }
        if (matches(d, "taxi", "bolt", "indriver", "essence", "carburant", "gasoil", "sans plomb", "95", "98", "diesel", "parking", "peage", "bus", "train", "transport", "voiture", "pneu", "lavage", "mécanique", "vidange", "freins", "assurance voiture", "vignette")) {
            return findOrCreateCategory("Transport", user, "DEPENSE");
        }
        if (matches(d, "chaussure", "habit", "vêtement", "shopping", "pull", "chemise", "boutique", "mode", "basket", "paire", "sneakers", "talon", "marque", "sac", "chapeau", "lunettes", "bijou", "montre", "veste", "jeans", "parapluie", "sac à dos", "valise", "manteau", "costume", "robe", "jupe", "t-shirt", "basket")) {
            return findOrCreateCategory("Shopping", user, "DEPENSE");
        }
        if (matches(d, "pharma", "médicament", "dentiste", "santé", "hôpital", "soin", "cardiologue", "ophtalmo", "clinique", "médecin", "docteur", "analyse", "dent", "vue", "lunette", "visite medicale", "consultation")) {
            return findOrCreateCategory("Santé", user, "DEPENSE");
        }
        if (matches(d, "steg", "sonede", "loyer", "internet", "telecom", "ooredoo", "orange", "topnet", "électricité", "facture", "eau", "gaz", "meuble", "réparation", "peinture", "ampoule", "loyer", "syndic", "climatiseur", "frigo")) {
            return findOrCreateCategory("Logement", user, "DEPENSE");
        }
        if (matches(d, "cinéma", "netflix", "spotify", "sport", "salle", "club", "vacances", "voyage", "abonnement", "loisirs", "disney", "prime", "jeu", "gaming", "ps5", "xbox", "match", "foot", "café", "sortie")) {
            return findOrCreateCategory("Loisirs", user, "DEPENSE");
        }
        if (matches(d, "coiffeur", "barbier", "beauté", "cosmétique", "soin", "esthétique", "maquillage", "parfum", "ongles", "dentifrice", "brosse à dents", "douche", "savon", "shampoing", "epilation", "coiffure")) {
            return findOrCreateCategory("Beauté", user, "DEPENSE");
        }
        if (matches(d, "salaire", "virement", "prime", "bonus", "gain", "revenu", "reçu", "argent", "remboursement")) {
            return findOrCreateCategory("Revenu", user, "REVENU");
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
            String prompt = "Tu es un expert en classification budgétaire. Ta mission est de classer chaque dépense dans la catégorie la plus logique de la liste.\n" +
                           "LISTE DES CATÉGORIES : " + cats + "\n\n" +
                           "RÈGLES CRITIQUES :\n" +
                           "1. Ne réponds JAMAIS 'Autre' si le mot a un sens (ex: 'veste' -> Shopping, 'burger' -> Alimentation).\n" +
                           "2. Si le mot n'est pas dans la liste, choisis la catégorie parente la plus proche.\n" +
                           "3. Réponds uniquement par UN SEUL MOT (le nom de la catégorie).\n\n" +
                           "MOT À CLASSER : '" + text + "'\n" +
                           "RÉPONSE :";

            Map<String, Object> options = Map.of(
                "temperature", 0.0,
                "num_predict", 32,      
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
                    .timeout(Duration.ofSeconds(5)) // Timeout réduit pour une réponse rapide
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(">>> [Ollama] Status: " + response.statusCode());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String res = root.path("response").asText().trim();
                System.out.println(">>> [Ollama] AI Response: " + res);
                
                // On cherche si la réponse de l'IA contient ou ressemble à une de nos catégories
                for (Categorie cat : existing) {
                    String catNom = cat.getNom().toLowerCase();
                    String aiRes = res.toLowerCase();
                    if (aiRes.contains(catNom) || diceCoefficient(aiRes, catNom) > 0.6) {
                        return cat.getNom();
                    }
                }
                
                // Si pas de match trouvé, on nettoie le premier mot
                if (res.contains(" ")) res = res.split(" ")[0];
                res = res.replaceAll("[^a-zA-Z\u00C0-\u024F]", "").trim();
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
            String prompt = "Tu es un assistant financier expert. Catégorise cette transaction : \"" + text + "\".\n" +
                           "CATÉGORIES POSSIBLES : [" + existingList + "].\n" +
                           "CONSIGNE : Choisis la catégorie la plus pertinente. Interdiction de répondre 'Autre' sauf si le texte est totalement incompréhensible. Réponds uniquement le nom de la catégorie.";

            Map<String, Object> body = Map.of("contents", new Object[]{Map.of("parts", new Object[]{Map.of("text", prompt)})});
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash-latest:generateContent?key=" + getCleanKey()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String res = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText().trim();
                System.out.println(">>> [Gemini] AI Response: " + res);
                
                for (Categorie cat : existing) {
                    if (res.toLowerCase().contains(cat.getNom().toLowerCase())) {
                        return cat.getNom();
                    }
                }
                return res;
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