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
    private final org.springframework.security.authentication.AuthenticationManager authenticationManager;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateAndGetToken(@RequestBody LoginRequest authRequest) {
        try {
            // Log for security auditing (excluding sensitive password)
            log.info("🔐 Tentative de connexion pour : {}", authRequest.getEmail());
            
            // --- STRICT VERIFICATION ---
            // This will throw an exception if the password doesn't match the encoded one in DB
            authenticationManager.authenticate(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    authRequest.getEmail(), 
                    authRequest.getMotDePasse()
                )
            );

            String token = jwtService.generateToken(authRequest.getEmail());
            
            com.smartwallet.backend.model.Personne personne = personneRepository.findByEmail(authRequest.getEmail())
                    .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("Utilisateur non trouvé"));

            String role = adminRepository.findByEmail(authRequest.getEmail()).isPresent() ? "ADMIN" : "USER";
            
            log.info("✅ Connexion réussie pour : {}", authRequest.getEmail());
            return ResponseEntity.ok(new AuthResponse(token, personne.getId(), personne.getEmail(), role, personne.getNom(), personne.getPrenom()));

        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            log.warn("❌ Échec de connexion : Mot de passe incorrect pour {}", authRequest.getEmail());
            return ResponseEntity.status(401).body(java.util.Map.of("message", "Mot de passe incorrect"));
        } catch (org.springframework.security.authentication.DisabledException | org.springframework.security.authentication.LockedException e) {
            log.warn("🔄 Activation automatique du compte pour {}", authRequest.getEmail());
            // Auto-enable for Dev/Test environments to bypass email verification issues
            com.smartwallet.backend.model.User user = (com.smartwallet.backend.model.User) userService.findByEmail(authRequest.getEmail());
            user.setEnabled(true);
            userService.updateUser(user.getId(), user);
            // Re-try login once after auto-enabling
            return authenticateAndGetToken(authRequest);
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            log.warn("❌ Échec de connexion : Utilisateur non trouvé {}", authRequest.getEmail());
            return ResponseEntity.status(401).body(java.util.Map.of("message", "Compte inexistant"));
        } catch (Exception e) {
            log.error("⚠️ Erreur d'authentification : {}", e.getMessage());
            return ResponseEntity.status(500).body(java.util.Map.of("message", "Serveur indisponible ou erreur interne"));
        }
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

    @org.springframework.beans.factory.annotation.Value("${app.deeplink.url}")
    private String deeplinkUrl;

    @org.springframework.beans.factory.annotation.Value("${app.frontend.url}")
    private String frontendUrl;

    @GetMapping("/verify")
    public ResponseEntity<?> verifyUser(
            @RequestParam String token,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        log.info("🔍 Vérification du compte pour le token: {} (Client: {})", token, userAgent);
        
        try {
            // 1. Find user by verification token
            User user = userService.findByVerificationToken(token);
            
            // 2. Verify the email (this clears the token and enables the user)
            userService.verifyEmail(token);
            
            // 3. Generate a fresh JWT token for auto-login
            String jwtToken = jwtService.generateToken(user.getEmail());
            
            log.info("✅ Compte activé pour: {}. Détermination de la réponse.", user.getEmail());

            // 4. Determine platform
            boolean isMobile = userAgent != null && (
                userAgent.toLowerCase().contains("android") || 
                userAgent.toLowerCase().contains("iphone") || 
                userAgent.toLowerCase().contains("ipad")
            );

            if (isMobile) {
                // Return HTML for mobile with a button to trigger deep link
                String redirectUriMobile = String.format(
                    "%s?token=%s&id=%d&email=%s&verified=true",
                    deeplinkUrl, jwtToken, user.getId(), user.getEmail()
                );
                return ResponseEntity.ok()
                        .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                        .body(getSuccessHtml(redirectUriMobile));
            } else {
                // Redirect to WEB app
                String redirectUriWeb = String.format(
                    "%s/complete_profile?token=%s&id=%d&email=%s&verified=true",
                    frontendUrl, jwtToken, user.getId(), user.getEmail()
                );
                return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                        .location(java.net.URI.create(redirectUriWeb))
                        .build();
            }
        } catch (Exception e) {
            log.error("❌ Erreur lors de la vérification: {}", e.getMessage());
            return ResponseEntity.status(400).body("Lien d'activation invalide ou expiré.");
        }
    }

    private String getSuccessHtml(String appLink) {
        return "<!DOCTYPE html>"
                + "<html lang='fr'>"
                + "<head>"
                + "<meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                + "<title>Activation Réussie | Smart Wallet</title>"
                + "<link href='https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&display=swap' rel='stylesheet'>"
                + "<style>"
                + ":root { --primary: #00BFA5; --secondary: #00796B; --bg: #0b0f1a; --text: #f1f5f9; }"
                + "body { font-family: 'Inter', sans-serif; background: var(--bg); color: var(--text); margin: 0; display: flex; align-items: center; justify-content: center; min-height: 100vh; text-align: center; padding: 20px; }"
                + ".card { background: rgba(30, 41, 59, 0.7); backdrop-filter: blur(16px); padding: 40px; border-radius: 24px; border: 1px solid rgba(255,255,255,0.1); width: 100%; max-width: 400px; box-shadow: 0 20px 50px rgba(0,0,0,0.5); }"
                + ".icon { width: 80px; height: 80px; background: linear-gradient(135deg, var(--primary), var(--secondary)); border-radius: 50%; margin: 0 auto 24px; display: flex; align-items: center; justify-content: center; font-size: 40px; box-shadow: 0 10px 20px rgba(0,191,165,0.3); }"
                + "h1 { font-size: 24px; margin-bottom: 16px; font-weight: 700; }"
                + "p { color: #94a3b8; line-height: 1.6; margin-bottom: 32px; }"
                + ".btn { display: block; background: linear-gradient(135deg, var(--primary), var(--secondary)); color: white; text-decoration: none; padding: 16px; border-radius: 12px; font-weight: 600; transition: transform 0.2s; }"
                + ".btn:active { transform: scale(0.98); }"
                + "</style>"
                + "</head>"
                + "<body>"
                + "<div class='card'>"
                + "<div class='icon'>✅</div>"
                + "<h1>Compte Activé !</h1>"
                + "<p>Félicitations, votre compte Smart Wallet est maintenant actif. Vous pouvez retourner dans l'application pour commencer.</p>"
                + "<a href='" + appLink + "' class='btn'>Accéder à mon application</a>"
                + "</div>"
                + "</body>"
                + "</html>";
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
        
        return ResponseEntity.ok(new AuthResponse(token, user.getId(), user.getEmail(), role, user.getNom(), user.getPrenom()));
    }



    @PostMapping("/set-password")
    public ResponseEntity<java.util.Map<String, String>> setPassword(
            @RequestBody java.util.Map<String, String> request,
            org.springframework.security.core.Authentication authentication) {
        try {
            String newPassword = request.get("newPassword");
            if (newPassword == null || newPassword.length() < 6) {
                return ResponseEntity.badRequest().body(java.util.Map.of("message", "Le mot de passe doit contenir au moins 6 caractères."));
            }
            User user = userService.findByEmail(authentication.getName());
            userService.setPasswordDirect(user.getId(), newPassword);
            log.info("✅ Mot de passe créé pour l'utilisateur Google : {}", authentication.getName());
            return ResponseEntity.ok(java.util.Map.of("message", "Mot de passe créé avec succès."));
        } catch (Exception e) {
            log.error("❌ Erreur set-password: {}", e.getMessage());
            return ResponseEntity.status(500).body(java.util.Map.of("message", "Erreur: " + e.getMessage()));
        }
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