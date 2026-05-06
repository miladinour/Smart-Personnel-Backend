package com.smartwallet.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private Long id;
    private String email;
    private String role;
    private String nom;
    private String prenom;

    public AuthResponse(String token, Long id, String email, String role) {
        this.token = token;
        this.id = id;
        this.email = email;
        this.role = role;
    }
}

