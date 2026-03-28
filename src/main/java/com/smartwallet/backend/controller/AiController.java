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
    private final UserService userService;

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
        // Mocking some seasonal alerts if no service is dedicated yet
        List<SeasonalAlert> alerts = new ArrayList<>();
        alerts.add(new SeasonalAlert(
            "Rentrée Scolaire", 
            "Septembre", 
            "📚", 
            "Attention : Vos dépenses augmentent de 30% historiquement en septembre.", 
            java.math.BigDecimal.valueOf(500), 
            30, 
            true
        ));
        return ResponseEntity.ok(alerts);
    }
}
