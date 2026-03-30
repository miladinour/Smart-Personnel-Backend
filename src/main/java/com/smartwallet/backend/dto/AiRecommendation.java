package com.smartwallet.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiRecommendation {
    private String title;
    private String message;
    private String type; // info, warning, success
    private String category;
    private String explanation; // Detailed explanation for "En savoir plus"
    private boolean challenge;
    private String challengeCategory; // The backend category name to watch (e.g., "Shopping")
}
