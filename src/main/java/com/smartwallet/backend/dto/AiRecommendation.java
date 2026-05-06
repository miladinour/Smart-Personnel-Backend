package com.smartwallet.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class AiRecommendation {
    private String title;
    private String message;
    private String type; // info, warning, success
    private String category;
    private String explanation; // Detailed explanation for "En savoir plus"
    private boolean challenge;
    private String challengeCategory; // The backend category name to watch (e.g., "Shopping")
    private LocalDateTime timestamp;
    private Map<String, String> params; // Dynamic variables for i18n

    public AiRecommendation(String title, String message, String type, String category, String explanation, boolean challenge, String challengeCategory) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.category = category;
        this.explanation = explanation;
        this.challenge = challenge;
        this.challengeCategory = challengeCategory;
        this.timestamp = LocalDateTime.now();
        this.params = new HashMap<>();
    }

    public AiRecommendation(String title, String message, String type, String category, String explanation, boolean challenge, String challengeCategory, Map<String, String> params) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.category = category;
        this.explanation = explanation;
        this.challenge = challenge;
        this.challengeCategory = challengeCategory;
        this.timestamp = LocalDateTime.now();
        this.params = params != null ? params : new HashMap<>();
    }
}
