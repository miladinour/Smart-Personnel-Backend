package com.smartwallet.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleLoginRequest {
    private String email;
    private String displayName;
    private String photoUrl;
    private String idToken; // Optionnel, pour une vérification plus poussée si nécessaire
}
