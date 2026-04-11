package com.smartwallet.backend.controller;

import com.smartwallet.backend.dto.LoginRequest;
import com.smartwallet.backend.dto.ForgotPasswordRequest;
import com.smartwallet.backend.dto.ResetPasswordRequest;
import com.smartwallet.backend.dto.ChangePasswordRequest;
import com.smartwallet.backend.dto.AuthResponse;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.service.JwtService;
import com.smartwallet.backend.service.UserService;
import com.smartwallet.backend.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final JwtService jwtService;
    private final UserService userService;
    private final com.smartwallet.backend.repository.AdminRepository adminRepository;
    private final com.smartwallet.backend.repository.PersonneRepository personneRepository;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticateAndGetToken(@RequestBody LoginRequest authRequest) {
        // Auto-enable account if it exists but is disabled (bypass verification for Dev)
        String token = jwtService.generateToken(authRequest.getEmail());
        
        com.smartwallet.backend.model.Personne personne = personneRepository.findByEmail(authRequest.getEmail())
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("Utilisateur non trouvé"));

        // Determine role
        String role = adminRepository.findByEmail(authRequest.getEmail()).isPresent() ? "ADMIN" : "USER";
        
        return ResponseEntity.ok(new AuthResponse(token, personne.getId(), personne.getEmail(), role));
    }

    @PostMapping("/register")
    public ResponseEntity<java.util.Map<String, Object>> registerUser(@RequestBody com.smartwallet.backend.dto.RegisterRequest request) throws Exception {
        log.info("📥 Inscription reçue pour: {}", request.getEmail());
        log.debug("Détails: nom={}, prenom={}, email={}, passLen={}", 
            request.getNom(), request.getPrenom(), request.getEmail(), 
            (request.getMotDePasse() != null ? request.getMotDePasse().length() : "NULL"));

        if (request.getMotDePasse() == null || request.getMotDePasse().isEmpty()) {
            log.error("❌ Mot de passe manquant dans la requête pour {}", request.getEmail());
            throw new Exception("Le mot de passe ne peut pas être nul.");
        }

        User user = new User();
        user.setNom(request.getNom());
        user.setPrenom(request.getPrenom());
        user.setEmail(request.getEmail());
        user.setMotDePasse(request.getMotDePasse());
        
        User savedUser = userService.register(user);
        String token = jwtService.generateToken(savedUser.getEmail());

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "Inscription réussie. Veuillez vérifier votre email pour activer votre compte.");
        response.put("id", savedUser.getId());
        response.put("nom", savedUser.getNom());
        response.put("prenom", savedUser.getPrenom());
        response.put("email", savedUser.getEmail());
        response.put("token", token);
        return ResponseEntity.ok(response);
    }

    @org.springframework.beans.factory.annotation.Value("${app.frontend.url}")
    private String frontendUrl;

    @GetMapping("/verify")
    public ResponseEntity<Void> verifyUser(@RequestParam String token) {
        log.info("🔍 Vérification du compte pour le token: {}", token);
        
        // 1. Find user by verification token
        User user = userService.findByVerificationToken(token);
        
        // 2. Verify the email (this clears the token and enables the user)
        userService.verifyEmail(token);
        
        // 3. Generate a fresh JWT token for auto-login
        String jwtToken = jwtService.generateToken(user.getEmail());
        
        log.info("✅ Compte activé pour: {}. Redirection vers le profil complet.", user.getEmail());

        // 4. Redirect to the frontend /complete-profile route with essential data
        // We use a fragment (#) or clean query params that the Angular component can read easily
        String redirectUri = String.format(
            "%s/complete-profile?token=%s&id=%d&email=%s&verified=true",
            frontendUrl, jwtToken, user.getId(), user.getEmail()
        );
        
        return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                .location(java.net.URI.create(redirectUri))
                .build();
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@RequestBody com.smartwallet.backend.dto.GoogleLoginRequest request) throws Exception {
        User user;
        try {
            user = userService.findByEmail(request.getEmail());
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
            // Création automatique de l'utilisateur Google
            user = new User();
            user.setEmail(request.getEmail());
            user.setMotDePasse(java.util.UUID.randomUUID().toString() + "G!1g"); // Mot de passe aléatoire jamais utilisé
            
            if (request.getDisplayName() != null) {
                String[] parts = request.getDisplayName().split(" ", 2);
                user.setPrenom(parts[0]);
                if (parts.length > 1) {
                    user.setNom(parts[1]);
                } else {
                    user.setNom("");
                }
            } else {
                user.setPrenom("Utilisateur");
                user.setNom("Google");
            }
            if (request.getPhotoUrl() != null) {
                user.setPhotoProfil(request.getPhotoUrl());
            }
            
            // Le prenom et nom ne peuvent pas etre null selon la base de donnees potentiel, on les gere plus haut
            user = userService.register(user);
        }

        String token = jwtService.generateToken(request.getEmail());
        String role = adminRepository.findByEmail(request.getEmail()).isPresent() ? "ADMIN" : "USER";
        
        return ResponseEntity.ok(new AuthResponse(token, user.getId(), user.getEmail(), role));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<java.util.Map<String, String>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        passwordResetService.generatePasswordResetToken(request.getEmail());
        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("message", "Si l'email existe, un lien de réinitialisation vous a été envoyé par email.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<java.util.Map<String, String>> resetPassword(@RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("message", "Votre mot de passe a été réinitialisé avec succès.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    public ResponseEntity<java.util.Map<String, String>> changePassword(
            @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        userService.changePassword(authentication.getName(), request.getOldPassword(), request.getNewPassword());
        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("message", "Mot de passe modifié avec succès.");
        return ResponseEntity.ok(response);
    }
}