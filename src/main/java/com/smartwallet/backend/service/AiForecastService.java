package com.smartwallet.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwallet.backend.dto.AiForecast;
import com.smartwallet.backend.model.Depense;
import com.smartwallet.backend.model.Revenu;
import com.smartwallet.backend.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiForecastService {

    private final DepenseService depenseService;
    private final RevenuService revenuService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key:YOUR_API_KEY_HERE}")
    private String geminiApiKey;

    @Value("${ollama.base.url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model.name:llama3}")
    private String ollamaModelName;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public List<AiForecast> getForecasts(User user) {
        System.out.println(">>> AI Forecast - Processing REAL AI for user: " + user.getEmail());
        
        // 1. Fetch History Data (Last 60 days for better trend detection)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sixtyDaysAgo = now.minusDays(60);
        
        List<Depense> historyDepenses = depenseService.getDepensesByUser(user, sixtyDaysAgo, now, null);
        List<Revenu> historyRevenus = revenuService.getRevenusByUser(user, sixtyDaysAgo, now, null);

        // --- ÉTAPE 1 : TENTER OLLAMA (Local First) ---
        System.out.println(">>> AI Forecast - Step 1: Trying Local IA (Ollama)...");
        List<AiForecast> localResult = callOllamaForForecast(user, historyDepenses, historyRevenus);
        if (localResult != null && !localResult.isEmpty()) {
            System.out.println(">>> AI Forecast - Success with Local IA!");
            return localResult;
        }

        // --- ÉTAPE 2 : TENTER GEMINI (Fallback Cloud) ---
        if (geminiApiKey != null && geminiApiKey.length() > 10) {
            System.out.println(">>> AI Forecast - Step 2: Local IA failed, falling back to Gemini Cloud...");
            List<AiForecast> llmResult = callGeminiForForecast(user, historyDepenses, historyRevenus);
            if (llmResult != null && !llmResult.isEmpty()) {
                System.out.println(">>> AI Forecast - Success with Gemini Cloud!");
                return llmResult;
            }
        }

        // 3. Fallback to Statistical/Manual Calculation
        return generateStatisticalForecast(user, historyDepenses, historyRevenus);
    }

    private List<AiForecast> callGeminiForForecast(User user, List<Depense> depenses, List<Revenu> revenus) {
        try {
            // Aggregate data by month & category for the prompt
            String context = formatHistoryContext(depenses, revenus);
            
            String prompt = "Tu es un expert en prévisions financières. Voici l'historique de l'utilisateur sur les 60 derniers jours :\n" +
                    context + "\n" +
                    "Génère une prévision pour les 3 PROCHAINS MOIS.\n" +
                    "Règles :\n" +
                    "1. Prédit le Revenu Total et la Dépense Totale par mois.\n" +
                    "2. Identifie les 3 catégories de dépenses les plus importantes.\n" +
                    "3. Fournis des conseils (insights) concrets.\n" +
                    "4. RÉPONDS UNIQUEMENT AU FORMAT JSON (liste d'objets) :\n" +
                    "[ {\"month\": \"Octobre 2024\", \"category\": null, \"predictedIncome\": 3000, \"predictedExpenses\": 2500, \"optimisticExpenses\": 2100, \"pessimisticExpenses\": 2900, \"confidence\": 0.9, \"insights\": [\"...\"]}, " +
                    "  {\"month\": \"Octobre 2024\", \"category\": \"Alimentation\", \"predictedIncome\": 0, \"predictedExpenses\": 600, \"confidence\": 0.8, \"insights\": []} ]";

            Map<String, Object> requestBody = Map.of(
                "contents", new Object[]{
                    Map.of("parts", new Object[]{
                        Map.of("text", prompt)
                    })
                },
                "generationConfig", Map.of(
                    "response_mime_type", "application/json"
                )
            );

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            String model = "gemini-1.5-flash"; // Priority model per user instructions
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + geminiApiKey))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String jsonText = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
                return objectMapper.readValue(jsonText, new TypeReference<List<AiForecast>>() {});
            }
        } catch (Exception e) {
            System.err.println(">>> AI Forecast LLM Error: " + e.getMessage());
        }
        return null;
    }

    private String formatHistoryContext(List<Depense> depenses, List<Revenu> revenus) {
        StringBuilder sb = new StringBuilder();
        sb.append("Dépenses :\n");
        Map<String, BigDecimal> depByCat = depenses.stream()
                .collect(Collectors.groupingBy(d -> d.getCategorie() != null ? d.getCategorie().getNom() : "Autre",
                        Collectors.mapping(Depense::getMontant, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        depByCat.forEach((cat, amount) -> sb.append("- ").append(cat).append(" : ").append(amount).append("\n"));
        
        sb.append("\nRevenus :\n");
        BigDecimal totalRev = revenus.stream().map(Revenu::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
        sb.append("- Total : ").append(totalRev).append("\n");
        
        return sb.toString();
    }

    private List<AiForecast> generateStatisticalForecast(User user, List<Depense> recentDepenses, List<Revenu> recentRevenus) {
        List<AiForecast> forecasts = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        BigDecimal totalExpenses = recentDepenses.stream().map(Depense::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIncome = recentRevenus.stream().map(Revenu::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal dailyExpenseAvg = totalExpenses.divide(new BigDecimal(60), 2, RoundingMode.HALF_UP);
        BigDecimal dailyIncomeAvg = totalIncome.divide(new BigDecimal(60), 2, RoundingMode.HALF_UP);

        for (int i = 1; i <= 3; i++) {
            LocalDateTime targetMonth = now.plusMonths(i);
            String monthName = targetMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH) + " " + targetMonth.getYear();
            
            BigDecimal predictedExpenses = dailyExpenseAvg.multiply(new BigDecimal(30)).multiply(new BigDecimal(1.0 + (i * 0.03)));
            BigDecimal predictedIncome = dailyIncomeAvg.multiply(new BigDecimal(30));

            List<String> insights = new ArrayList<>();
            insights.add("ai.forecast.stability_insight");

            forecasts.add(new AiForecast(
                monthName,
                null,
                predictedIncome.setScale(2, RoundingMode.HALF_UP),
                predictedExpenses.setScale(2, RoundingMode.HALF_UP),
                predictedExpenses.multiply(new BigDecimal(0.9)).setScale(2, RoundingMode.HALF_UP),
                predictedExpenses.multiply(new BigDecimal(1.1)).setScale(2, RoundingMode.HALF_UP),
                0.8,
                insights
            ));
        }
        return forecasts;
    }

    private List<AiForecast> callOllamaForForecast(User user, List<Depense> depenses, List<Revenu> revenus) {
        try {
            String context = formatHistoryContext(depenses, revenus);
            String prompt = "Tu es un expert financier. Voici l'historique sur 60 jours :\n" +
                    context + "\n" +
                    "Génère une prévision JSON pour les 3 PROCHAINS MOIS.\n" +
                    "Format : [ {\"month\": \"Nom\", \"predictedIncome\": 0, \"predictedExpenses\": 0, \"optimisticExpenses\": 0, \"pessimisticExpenses\": 0, \"confidence\": 0.8, \"insights\": []} ]";

            Map<String, Object> body = Map.of(
                "model",  ollamaModelName,
                "prompt", prompt,
                "stream", false,
                "options", Map.of("temperature", 0.1, "num_predict", 1024)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String resText = root.path("response").asText().trim();
                
                if (resText.contains("[")) {
                    resText = resText.substring(resText.indexOf("["), resText.lastIndexOf("]") + 1);
                }
                
                return objectMapper.readValue(resText, new TypeReference<List<AiForecast>>() {});
            }
        } catch (Exception e) {
            System.err.println(">>> [Ollama] Forecast failed: " + e.getMessage());
        }
        return null;
    }
}
