package com.smartwallet.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.model.Categorie;
import com.smartwallet.backend.dto.AiCommandResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiCommandService {

    private final AiCategorizationService aiCategorizationService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    public AiCommandService(AiCategorizationService aiCategorizationService) {
        this.aiCategorizationService = aiCategorizationService;
    }

    public AiCommandResponse processCommand(String command, String devise, User user) {
        System.out.println(">>> [AiCommand] processing: '" + command + "'");
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        
        try {
            String prompt = "Tu es un assistant financier. Aujourd'hui est le : " + today + ".\n" +
                          "Analyse : \"" + command + "\".\n" +
                          "Réponds UNIQUEMENT en JSON :\n" +
                          "{\"action\": \"ADD_TRANSACTION\", \"params\": {\"amount\": 15.0, \"description\": \"Libellé\", \"type\": \"DEPENSE\", \"category\": \"Alimentation\", \"date\": \"yyyy-MM-dd\"}, \"responseMessage\": \"Ok !\"}";

            Map<String, Object> body = Map.of(
                "contents", new Object[]{Map.of("parts", new Object[]{Map.of("text", prompt)})}
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash-latest:generateContent?key=" + getCleanKey()))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String resultText = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText().trim();
                
                if (resultText.contains("{")) {
                    resultText = resultText.substring(resultText.indexOf("{"), resultText.lastIndexOf("}") + 1);
                }
                
                return objectMapper.readValue(resultText, AiCommandResponse.class);
            }
        } catch (Exception e) {}

        if (command.toUpperCase().contains("ANALYSE DE FACTURE")) {
            return processOcrFallback(command, user);
        }

        return fallbackWithRegex(command, user);
    }

    private String getCleanKey() {
        return (geminiApiKey != null) ? geminiApiKey.trim().split(" ")[0] : "";
    }

    private AiCommandResponse processOcrFallback(String command, User user) {
        String[] lines = command.split("\n");
        double maxAmount = 0.0;
        String merchant = "Facture";

        for (int i = 0; i < Math.min(lines.length, 5); i++) {
            if (lines[i].length() > 3 && !lines[i].contains("ANALYSE")) {
                merchant = lines[i].trim();
                break;
            }
        }

        Pattern p = Pattern.compile("(\\d{1,5}([.,]\\d{3}))|(\\d{1,5}([.,]\\d{1,2}))");
        Matcher m = p.matcher(command);
        while (m.find()) {
            try {
                double val = Double.parseDouble(m.group().replace(",", "."));
                if (val > maxAmount && val < 5000) maxAmount = val;
            } catch (Exception e) {}
        }

        Categorie cat = aiCategorizationService.categorize(merchant + " " + command, "DEPENSE", user);

        AiCommandResponse res = new AiCommandResponse();
        res.setAction("ADD_TRANSACTION");
        res.setResponseMessage("Facture détectée : " + merchant);
        
        Map<String, Object> params = new HashMap<>();
        params.put("amount", maxAmount);
        params.put("description", merchant);
        params.put("type", "DEPENSE");
        params.put("category", cat.getNom());
        params.put("date", LocalDate.now().format(DateTimeFormatter.ISO_DATE));
        
        res.setParams(params);
        return res;
    }

    private AiCommandResponse fallbackWithRegex(String command, User user) {
        String cmdLower = command.toLowerCase();
        String dateStringFound = "";
        
        // 1. Détection de la date
        LocalDate dateVal = LocalDate.now();
        if (cmdLower.contains("avant-hier") || cmdLower.contains("avant hier")) {
            dateVal = dateVal.minusDays(2);
            dateStringFound = cmdLower.contains("avant-hier") ? "avant-hier" : "avant hier";
        } else if (cmdLower.contains("hier")) {
            dateVal = dateVal.minusDays(1);
            dateStringFound = "hier";
        } else {
            // Détection de format DD/MM/YYYY ou DD MM YYYY ou DD.MM.YYYY
            Pattern datePattern = Pattern.compile("(\\b\\d{1,2}[/\\.\\s]\\d{1,2}([/\\.\\s]\\d{2,4})?\\b)");
            Matcher dateMatcher = datePattern.matcher(command);
            if (dateMatcher.find()) {
                try {
                    dateStringFound = dateMatcher.group(1);
                    String[] parts = dateStringFound.split("[/\\.\\s]");
                    int day = Integer.parseInt(parts[0]);
                    int month = Integer.parseInt(parts[1]);
                    int year = (parts.length == 3) ? (parts[2].length() == 2 ? 2000 + Integer.parseInt(parts[2]) : Integer.parseInt(parts[2])) : LocalDate.now().getYear();
                    dateVal = LocalDate.of(year, month, day);
                } catch (Exception e) {}
            }
        }

        // 2. Détection du montant
        Pattern p = Pattern.compile("(\\d+([.,]\\d+)?)");
        Matcher m = p.matcher(command);
        
        if (m.find()) {
            String amountStr = m.group(1);
            double amt = Double.parseDouble(amountStr.replace(",", "."));

            // 3. Détection du type
            String type = "DEPENSE";
            if (cmdLower.contains("salaire") || cmdLower.contains("revenu") || cmdLower.contains("reçu") || cmdLower.contains("gain")) {
                type = "REVENU";
            }
            
            // 4. Nettoyage de la description (on enlève aussi précisément la date trouvée)
            String description = cmdLower
                    .replace(amountStr, "")
                    .replace(dateStringFound, "") // On enlève la date détectée du titre !
                    .replaceAll("(?i)\\b(j'ai|dépensé|payé|ajouté|achat|un|le|la|du|de|dt|dinar|dinars|reçu|salaire|revenu|soir|matin|le)\\b", "")
                    .replaceAll("(?i)\\b(au|à la|à l'|à)\\b", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            
            if (description.isEmpty()) description = (type.equals("REVENU") ? "Revenu" : "Dépense");
            else description = Character.toUpperCase(description.charAt(0)) + description.substring(1);

            System.out.println(">>> [DEBUG] Final description sent to AI: '" + description + "'");
            Categorie cat = aiCategorizationService.categorize(description, type, user);
            
            AiCommandResponse res = new AiCommandResponse();
            res.setAction("ADD_TRANSACTION");
            res.setResponseMessage("Ok, enregistré pour le " + dateVal.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            
            Map<String, Object> params = new HashMap<>();
            params.put("amount", amt);
            params.put("description", description);
            params.put("type", type);
            params.put("category", cat.getNom());
            params.put("date", dateVal.format(DateTimeFormatter.ISO_DATE));
            
            res.setParams(params);
            return res;
        }

        AiCommandResponse res = new AiCommandResponse();
        res.setAction("TALK");
        res.setResponseMessage("Je vous écoute...");
        return res;
    }
}
