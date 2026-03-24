package com.smartwallet.backend.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
@Data
@AllArgsConstructor
public class LoginResponse {
    private String email;
    private String jwt;
}
