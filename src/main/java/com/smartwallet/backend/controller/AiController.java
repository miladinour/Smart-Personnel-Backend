package com.smartwallet.backend.controller;

import com.smartwallet.backend.dto.AiForecast;
import com.smartwallet.backend.dto.AiRecommendation;
import com.smartwallet.backend.dto.SeasonalAlert;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.service.AiForecastService;
import com.smartwallet.backend.service.AiRecommendationService;
import com.smartwallet.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import com.smartwallet.backend.dto.AiCommandRequest;
import com.smartwallet.backend.dto.AiCommandResponse;
import com.smartwallet.backend.service.AiCommandService;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AiController {

    private final AiRecommendationService aiRecommendationService;
    private final AiForecastService aiForecastService;
    private final AiCommandService aiCommandService;
    private final com.smartwallet.backend.service.AiCategorizationService aiCategorizationService;
    private final UserService userService;

    @PostMapping("/suggest-category")
    public ResponseEntity<java.util.Map<String, String>> suggestCategory(@RequestBody java.util.Map<String, String> request, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        String text = request.get("text");
        String type = request.getOrDefault("type", "DEPENSE");
        
        com.smartwallet.backend.model.Categorie cat = aiCategorizationService.categorize(text, type, user);
        
        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("category", cat.getNom());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/process-command")
    public ResponseEntity<AiCommandResponse> processCommand(@RequestBody AiCommandRequest request, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(aiCommandService.processCommand(request.getCommand(), request.getCurrentDevise(), user));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<AiRecommendation>> getRecommendations(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(aiRecommendationService.getRecommendations(user));
    }

    @GetMapping("/forecasts")
    public ResponseEntity<List<AiForecast>> getForecasts(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(aiForecastService.getForecasts(user));
    }

    @GetMapping("/seasonal-alerts")
    public ResponseEntity<List<SeasonalAlert>> getSeasonalAlerts(Authentication authentication) {
        List<SeasonalAlert> alerts = new ArrayList<>();
        
        // Use keys instead of hardcoded strings for full localization
        alerts.add(new SeasonalAlert(
            "seasonal.school.title", 
            "septembre", 
            "📚", 
            "seasonal.school.desc", 
            java.math.BigDecimal.valueOf(500), 
            30, 
            true
        ));

        alerts.add(new SeasonalAlert(
            "seasonal.summer.title", 
            "juillet", 
            "☀️", 
            "seasonal.summer.desc", 
            java.math.BigDecimal.valueOf(800), 
            25, 
            false
        ));

        alerts.add(new SeasonalAlert(
            "seasonal.ramadan.title", 
            "february", 
            "🌙", 
            "seasonal.ramadan.desc", 
            java.math.BigDecimal.valueOf(400), 
            20, 
            true
        ));

        alerts.add(new SeasonalAlert(
            "seasonal.eid_fitr.title", 
            "march", 
            "🍬", 
            "seasonal.eid_fitr.desc", 
            java.math.BigDecimal.valueOf(500), 
            30, 
            false
        ));

        alerts.add(new SeasonalAlert(
            "seasonal.eid_adha.title", 
            "may", 
            "🐑", 
            "seasonal.eid_adha.desc", 
            java.math.BigDecimal.valueOf(1500), 
            40, 
            true
        ));

        return ResponseEntity.ok(alerts);
    }
}
