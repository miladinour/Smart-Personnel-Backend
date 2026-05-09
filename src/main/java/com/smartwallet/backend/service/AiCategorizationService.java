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
import java.text.Normalizer;

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
        
        System.out.println(">>> [AiCategorization] Processing cascade for description: '" + text + "' (Type: " + type + ")");
        
        // Log hexadecimal to detect invisible characters
        StringBuilder hexStr = new StringBuilder();
        for (char ch : text.toCharArray()) {
            hexStr.append(String.format("%04x ", (int) ch));
        }
        System.out.println(">>> [AiCategorization] Hex of description: " + hexStr.toString().trim());

        String desc = text.toLowerCase().trim();

        // --- NIVEAU 1 : MOTS-CLÉS (Instantané) ---
        System.out.println(">>> [AiCategorization] Level 1: Checking Keywords...");
        Categorie fromKeywords = checkKeywords(desc, type, user);
        if (fromKeywords != null) {
            System.out.println(">>> [AiCategorization] Level 1 (Keywords) MATCHED: " + fromKeywords.getNom());
            return fromKeywords;
        }
        System.out.println(">>> [AiCategorization] Level 1 (Keywords) NO MATCH.");

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

        // NIVEAU 4 : Ollama (Local)
        System.out.println(">>> [AiCategorization] Level 4: Attempting Ollama (Model: " + ollamaModelName + ")...");
        String ollamaRes = callOllama(desc, type, allCats);
        if (ollamaRes != null && !ollamaRes.isBlank()) {
            System.out.println(">>> [AiCategorization] Level 4 (Ollama) MATCHED: '" + ollamaRes + "'");
            return findOrCreateCategory(ollamaRes, user, type);
        }
        System.out.println(">>> [AiCategorization] Level 4 (Ollama) FAILED or returned null.");

        // NIVEAU 5 : Gemini (Cloud Fallback)
        System.out.println(">>> [AiCategorization] Level 5: Attempting Gemini Fallback...");
        String geminiRes = callGemini(desc, type, allCats);
        if (geminiRes != null && !geminiRes.isBlank()) {
            System.out.println(">>> [AiCategorization] Level 5 (Gemini) MATCHED: '" + geminiRes + "'");
            return findOrCreateCategory(geminiRes, user, type);
        }
        System.out.println(">>> [AiCategorization] Level 5 (Gemini) FAILED.");

        // Si rien n'a marché, on met "Autre"
        System.out.println(">>> [AiCategorization] All levels failed. Defaulting to 'Autre'.");
        return findOrCreateCategory("Autre", user, type);
    }

    private Categorie checkKeywords(String desc, String type, User user) {
        String d = normalize(desc);
        System.out.println(">>> [AiCategorization] DEBUG: checkKeywords normalized input d = '" + d + "'");
        
        // --- PRIORITÉ 1 : MATCH EXACT AVEC LES NOMS DE CATÉGORIES DE L'USER ---
        List<Categorie> userCats = categorieRepository.findByUserOrUserIsNull(user);
        
        System.out.println(">>> [AiCategorization] Loaded " + userCats.size() + " categories for matching.");

        for (Categorie c : userCats) {
            if (normalize(c.getNom()).equals(d)) {
                System.out.println(">>> [AiCategorization] Priority 1 (Exact Match) found: " + c.getNom());
                return c;
            }
        }

        // --- PRIORITÉ 2 : MOTS-CLÉS SPÉCIFIQUES ---
        if (matches(d, "cadeau", "cadeaux", "fleur", "bouquet", "anniversaire", "fête", "don", "mariage", "naissance", "fleurs", "maman", "mama", "surprise", "kdo")) {
            return findOrCreateCategory("Cadeau", user, type);
        }
        if (matches(d, "monoprix", "carrefour", "mg", "restau", "manger", "viande", "boulangerie", "lait", "pain", "pizza", "café", "alimentation", "épicerie", "fruits", "légumes", "kfc", "mac", "food", "lidl", "supermarché", "resto", "diner", "déjeuner", "snack", "sandwich", "gâteau", "gateau", "pâtisserie", "chocolat", "eau mineral", "oeufs", "poulet", "bouteille", "boisson", "jus", "yaourt", "fromage", "beurre", "sucre", "farine", "huile", "sel", "epice", "mouton", "agneau", "poulet", "viande", "boeuf", "poisson", "merguez", "achat alimentaire", "marche", "epicerie")) {
            return findOrCreateCategory("Alimentation", user, type);
        }
        if (matches(d, "taxi", "bolt", "indriver", "essence", "carburant", "gasoil", "sans plomb", "95", "98", "diesel", "parking", "peage", "bus", "train", "transport", "voiture", "pneu", "lavage", "mécanique", "vidange", "freins", "assurance voiture", "vignette")) {
            return findOrCreateCategory("Transport", user, type);
        }
        if (matches(d, "chaussure", "habit", "vêtement", "shopping", "pull", "chemise", "boutique", "mode", "basket", "paire", "sneakers", "talon", "marque", "sac", "chapeau", "lunettes", "bijou", "montre", "veste", "jeans", "parapluie", "sac à dos", "valise", "manteau", "costume", "robe", "jupe", "t-shirt", "basket")) {
            return findOrCreateCategory("Shopping", user, type);
        }
        if (matches(d, "pharma", "médicament", "dentiste", "santé", "hôpital", "soin", "cardiologue", "ophtalmo", "clinique", "médecin", "docteur", "analyse", "dent", "vue", "lunette", "visite medicale", "consultation")) {
            return findOrCreateCategory("Santé", user, type);
        }
        if (matches(d, "steg", "sonede", "loyer", "internet", "telecom", "ooredoo", "orange", "topnet", "électricité", "facture eau", "facture gaz", "facture electricite", "gaz", "meuble", "réparation", "peinture", "ampoule", "syndic", "climatiseur", "frigo")) {
            return findOrCreateCategory("Logement", user, type);
        }
        if (matches(d, "cinema", "netflix", "spotify", "sport", "salle", "club", "vacances", "voyage", "abonnement", "loisirs", "disney", "prime", "jeu", "gaming", "ps5", "xbox", "match", "foot", "cafe", "sortie")) {
            return findOrCreateCategory("Loisirs", user, type);
        }
        if (matches(d, "livre", "livres", "roman", "bd", "manga", "librairie", "cours", "formation", "ecole", "universite", "scolarite", "crayon", "stylo", "cahier", "cartable", "calculatrice", "fourniture", "dictionnaire")) {
            return findOrCreateCategory("Education", user, type);
        }
        if (matches(d, "coiffeur", "barbier", "beauté", "cosmétique", "soin", "esthétique", "maquillage", "parfum", "ongles", "dentifrice", "brosse à dents", "douche", "savon", "shampoing", "epilation", "coiffure")) {
            return findOrCreateCategory("Beauté", user, type);
        }
        if (matches(d, "salaire", "virement", "prime", "bonus", "gain", "revenu", "reçu", "argent", "remboursement")) {
            return findOrCreateCategory("Revenu", user, type);
        }
        if (matches(d, "telephone", "telephonique", "reglage", "reparation", "depannage", "iphone", "samsung", "xiaomi", "android", "smartphone", "ecran", "batterie", "chargeur", "plombier", "electricien", "technicien", "maintenance", "installation", "assistance")) {
            return findOrCreateCategory("Services", user, type);
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
                    .timeout(Duration.ofSeconds(15)) // Augmenté pour laisser Ollama générer une réponse
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
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + getCleanKey()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String res = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText().trim();
                System.out.println(">>> [Gemini] AI Raw Response: '" + res + "'");
                
                // On essaie de mapper vers une catégorie existante
                for (Categorie cat : existing) {
                    String catNom = cat.getNom().toLowerCase();
                    if (res.toLowerCase().contains(catNom)) {
                        return cat.getNom();
                    }
                }
                
                // Nettoyage si l'IA a mis une phrase
                if (res.contains(" ")) res = res.split(" ")[0];
                res = res.replaceAll("[^a-zA-Z\u00C0-\u024F]", "").trim();
                return res;
            } else {
                System.err.println(">>> [Gemini] API Error: " + response.statusCode());
                if (response.statusCode() == 403 || response.statusCode() == 401) {
                    System.err.println(">>> [Gemini] CRITICAL: API Key might be invalid or restricted! Body: " + response.body());
                } else {
                    System.err.println(">>> [Gemini] Body: " + response.body());
                }
            }
        } catch (Exception e) {
            System.err.println(">>> [Gemini] Connection Exception: " + e.getMessage());
        }
        return null;
    }

    private String normalize(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    private boolean matches(String text, String... keywords) {
        if (text == null || text.isBlank()) return false;
        String lowerText = normalize(text);

        for (String k : keywords) {
            String lowerK = normalize(k);
            
            boolean matched = false;
            if (lowerK.length() <= 4) {
                // Pour les mots courts, on veut un match exact de mot (\b)
                if (lowerText.matches(".*\\b" + Pattern.quote(lowerK) + "\\b.*")) {
                    // On évite de matcher des mots trop communs seuls
                    if (!lowerText.equals("un") && !lowerText.equals("le")) {
                        matched = true;
                    }
                }
            } else {
                // Pour les mots plus longs, "cadeau" matchera "cadeaux" ou "cadeau d'anniv"
                if (lowerText.contains(lowerK)) matched = true;
                else if (diceCoefficient(lowerText, lowerK) > 0.80) matched = true;
            }

            if (matched) {
                System.out.println(">>> [AiCategorization] DEBUG: Keyword MATCH found! Keyword: '" + lowerK + "' in text: '" + lowerText + "'");
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