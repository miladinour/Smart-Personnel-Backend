package com.smartwallet.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

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

    public AiRecommendation(String title, String message, String type, String category, String explanation, boolean challenge, String challengeCategory) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.category = category;
        this.explanation = explanation;
        this.challenge = challenge;
        this.challengeCategory = challengeCategory;
        this.timestamp = LocalDateTime.now();
    }
}
