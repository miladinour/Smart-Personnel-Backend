package com.smartwallet.backend.controller;

import com.smartwallet.backend.dto.AiForecast;
import com.smartwallet.backend.dto.AiRecommendation;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.service.AiForecastService;
import com.smartwallet.backend.service.AiRecommendationService;
import com.smartwallet.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiRecommendationService aiRecommendationService;
    private final AiForecastService aiForecastService;
    private final UserService userService;

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
}
