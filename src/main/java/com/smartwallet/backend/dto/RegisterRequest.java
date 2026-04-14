package com.smartwallet.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    private String nom;
    private String prenom;
    private String email;
    @JsonProperty("motDePasse")
    private String motDePasse;
}
