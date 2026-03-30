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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AuthController {
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticateAndGetToken(@RequestBody LoginRequest authRequest) {
        // Auto-enable account if it exists but is disabled (bypass verification for Dev)
        User userExist = userService.findByEmail(authRequest.getEmail());
        if (userExist != null && !userExist.isEnabled()) {
            userExist.setEnabled(true);
            userService.updateUserStatus(userExist.getId(), true);
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(authRequest.getEmail(), authRequest.getMotDePasse()));


        String token = jwtService.generateToken(authRequest.getEmail());
        User user = userService.findByEmail(authRequest.getEmail());
        return ResponseEntity.ok(new AuthResponse(token, user.getId(), user.getEmail()));
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@RequestBody User user) throws Exception {
        userService.register(user);
        return ResponseEntity.ok("Inscription réussie. Veuillez vérifier votre email pour activer votre compte.");
    }

    @org.springframework.beans.factory.annotation.Value("${app.frontend.url}")
    private String frontendUrl;

    @GetMapping("/verify")
    public ResponseEntity<Void> verifyUser(@RequestParam String token) {
        // Find user by verification token before clearing it
        User user = userService.findByVerificationToken(token);
        
        // Verify the email (this clears the token and enables the user)
        userService.verifyEmail(token);
        
        // Generate a fresh JWT token for auto-login in the app
        String jwtToken = jwtService.generateToken(user.getEmail());
        
        // Redirect logic - For testing on Web, we redirect to the Flutter Web URL
        // In a real app, you might use a landing page or deeper logic here
        String redirectUri = String.format(
            "%s/#/complete_profile?token=%s&id=%d&email=%s",
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

        String token = jwtService.generateToken(user.getEmail());
        return ResponseEntity.ok(new AuthResponse(token, user.getId(), user.getEmail()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        passwordResetService.generatePasswordResetToken(request.getEmail());
        return ResponseEntity.ok("Si l'email existe, un lien de réinitialisation vous a été envoyé par email.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok("Votre mot de passe a été réinitialisé avec succès.");
    }

    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        userService.changePassword(authentication.getName(), request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.ok("Mot de passe modifié avec succès.");
    }
}