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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JwtService jwtService;
    private final UserService userService;
    private final com.smartwallet.backend.repository.AdminRepository adminRepository;
    private final com.smartwallet.backend.repository.PersonneRepository personneRepository;
    private final org.springframework.security.authentication.AuthenticationManager authenticationManager;
    private final PasswordResetService passwordResetService;
    private final String deeplinkUrl;
    private final String frontendUrl;

    public AuthController(
            JwtService jwtService,
            UserService userService,
            com.smartwallet.backend.repository.AdminRepository adminRepository,
            com.smartwallet.backend.repository.PersonneRepository personneRepository,
            org.springframework.security.authentication.AuthenticationManager authenticationManager,
            PasswordResetService passwordResetService,
            @org.springframework.beans.factory.annotation.Value("${app.deeplink.url}") String deeplinkUrl,
            @org.springframework.beans.factory.annotation.Value("${app.frontend.url}") String frontendUrl) {
        this.jwtService = jwtService;
        this.userService = userService;
        this.adminRepository = adminRepository;
        this.personneRepository = personneRepository;
        this.authenticationManager = authenticationManager;
        this.passwordResetService = passwordResetService;
        this.deeplinkUrl = deeplinkUrl;
        this.frontendUrl = frontendUrl;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateAndGetToken(@RequestBody LoginRequest authRequest) {
        try {
            log.info("🔐 Tentative de connexion pour : {}", authRequest.getEmail());
            authenticationManager.authenticate(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            authRequest.getEmail(),
                            authRequest.getMotDePasse()));
            String token = jwtService.generateToken(authRequest.getEmail());
            com.smartwallet.backend.model.Personne personne = personneRepository.findByEmail(authRequest.getEmail())
                    .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(
                            "Utilisateur non trouvé"));
            String role = adminRepository.findByEmail(authRequest.getEmail()).isPresent() ? "ADMIN" : "USER";
            log.info("✅ Connexion réussie pour : {}", authRequest.getEmail());
            return ResponseEntity.ok(new AuthResponse(token, personne.getId(), personne.getEmail(), role,
                    personne.getNom(), personne.getPrenom()));
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            return ResponseEntity.status(401).body(java.util.Map.of("message", "Mot de passe incorrect"));
        } catch (org.springframework.security.authentication.DisabledException e) {
            return ResponseEntity.status(401)
                    .body(java.util.Map.of("message", "Compte non activé. Veuillez vérifier votre boîte mail."));
        } catch (org.springframework.security.authentication.LockedException e) {
            return ResponseEntity.status(401)
                    .body(java.util.Map.of("message", "Compte verrouillé. Veuillez contacter l'administrateur."));
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            return ResponseEntity.status(401).body(java.util.Map.of("message", "Compte inexistant"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(java.util.Map.of("message", "Serveur indisponible ou erreur interne"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody com.smartwallet.backend.dto.RegisterRequest request) {
        log.info("📥 Inscription reçue pour: {}", request.getEmail());
        try {
            if (request.getMotDePasse() == null || request.getMotDePasse().isEmpty()) {
                return ResponseEntity.status(400).body(java.util.Map.of("message", "Le mot de passe est obligatoire."));
            }

            // Détecter l'URL de base dynamiquement
            String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
            log.info("🌐 URL de base détectée : {}", baseUrl);

            User user = new User();
            user.setNom(request.getNom());
            user.setPrenom(request.getPrenom());
            user.setEmail(request.getEmail());
            user.setMotDePasse(request.getMotDePasse());
            
            User savedUser = userService.register(user, baseUrl);
            
            String token = jwtService.generateToken(savedUser.getEmail());
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("message", "Inscription réussie. Veuillez vérifier votre email pour activer votre compte.");
            response.put("id", savedUser.getId());
            response.put("nom", savedUser.getNom());
            response.put("prenom", savedUser.getPrenom());
            response.put("email", savedUser.getEmail());
            response.put("token", token);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Erreur lors de l'inscription : {}", e.getMessage());
            String message = e.getMessage();
            if (message.contains("email")) {
                return ResponseEntity.status(400).body(java.util.Map.of("message", message));
            }
            return ResponseEntity.status(500).body(java.util.Map.of("message", "Une erreur technique est survenue."));
        }
    }

    // ✅ NOUVEAU : endpoint /activate qui redirige vers l'app après activation
    @GetMapping("/activate")
    public void activateAndRedirect(
            @RequestParam String token,
            @RequestParam String id,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String nom,
            @RequestParam(required = false) String prenom,
            HttpServletResponse response) throws IOException {

        log.info("🔗 Activation via lien email pour id: {}", id);

        try {
            // 1. Activer le compte
            userService.verifyEmail(token);
            log.info("✅ Compte activé avec succès pour token: {}", token);
        } catch (Exception e) {
            log.error("❌ Erreur activation: {}", e.getMessage());
            // On continue quand même la redirection même si déjà activé
        }

        // 2. Rediriger vers le deep link — Android/iOS intercepte et ouvre l'app
        String deepLink = "smartwallet://activate"
                + "?token=" + (token != null ? token : "")
                + "&id=" + id
                + "&email=" + (email != null ? email : "")
                + "&nom=" + (nom != null ? nom : "")
                + "&prenom=" + (prenom != null ? prenom : "");

        log.info("🚀 Redirection vers deep link: {}", deepLink);
        response.sendRedirect(deepLink);
    }

    // ✅ ANCIEN /verify gardé pour compatibilité web (retourne la page HTML)
    @GetMapping("/verify")
    public ResponseEntity<?> verifyUser(
            @RequestParam String token,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        log.info("🔍 Vérification du compte pour le token: {}", token);
        try {
            userService.findByVerificationToken(token);
            userService.verifyEmail(token);
            log.info("✅ Compte activé via /verify pour token: {}", token);
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                    .body(getSuccessHtml());
        } catch (Exception e) {
            log.error("❌ Erreur lors de la vérification: {}", e.getMessage());
            return ResponseEntity.status(400).body("Lien d'activation invalide ou expiré.");
        }
    }

    private String getSuccessHtml() {
        return "<!DOCTYPE html>"
                + "<html lang='fr'>"
                + "<head>"
                + "<meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                + "<title>Activation Réussie | Smart Wallet</title>"
                + "<link href='https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&display=swap' rel='stylesheet'>"
                + "<style>"
                + ":root { --primary: #00BFA5; --secondary: #00796B; --bg: #0b0f1a; --text: #f1f5f9; }"
                + "body { font-family: 'Inter', sans-serif; background: var(--bg); color: var(--text); margin: 0;"
                + " display: flex; align-items: center; justify-content: center; min-height: 100vh; text-align: center; padding: 20px; }"
                + ".card { background: rgba(30,41,59,0.7); backdrop-filter: blur(16px); padding: 40px;"
                + " border-radius: 24px; border: 1px solid rgba(255,255,255,0.1); width: 100%; max-width: 400px;"
                + " box-shadow: 0 20px 50px rgba(0,0,0,0.5); }"
                + ".icon { width: 80px; height: 80px; background: linear-gradient(135deg, var(--primary), var(--secondary));"
                + " border-radius: 50%; margin: 0 auto 24px; display: flex; align-items: center; justify-content: center;"
                + " font-size: 40px; box-shadow: 0 10px 20px rgba(0,191,165,0.3); }"
                + "h1 { font-size: 24px; margin-bottom: 16px; font-weight: 700; }"
                + "p { color: #94a3b8; line-height: 1.6; margin-bottom: 32px; }"
                + "</style>"
                + "</head>"
                + "<body>"
                + "<div class='card'>"
                + "<div class='icon'>✅</div>"
                + "<h1>Compte Activé !</h1>"
                + "<p>Votre compte Smart Wallet est maintenant actif.<br><br>"
                + "<b>Retournez à l'application pour compléter votre profil.</b></p>"
                + "</div>"
                + "</body>"
                + "</html>";
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@RequestBody com.smartwallet.backend.dto.GoogleLoginRequest request)
            throws Exception {
        User user;
        try {
            user = userService.findByEmail(request.getEmail());
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
            user = new User();
            user.setEmail(request.getEmail());
            user.setMotDePasse(java.util.UUID.randomUUID().toString() + "G!1g");
            if (request.getDisplayName() != null) {
                String[] parts = request.getDisplayName().split(" ", 2);
                user.setPrenom(parts[0]);
                user.setNom(parts.length > 1 ? parts[1] : "");
            } else {
                user.setPrenom("Utilisateur");
                user.setNom("Google");
            }
            if (request.getPhotoUrl() != null)
                user.setPhotoProfil(request.getPhotoUrl());
            
            String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
            user = userService.register(user, baseUrl);
        }
        String token = jwtService.generateToken(request.getEmail());
        String role = adminRepository.findByEmail(request.getEmail()).isPresent() ? "ADMIN" : "USER";
        return ResponseEntity
                .ok(new AuthResponse(token, user.getId(), user.getEmail(), role, user.getNom(), user.getPrenom()));
    }

    @PostMapping("/set-password")
    public ResponseEntity<java.util.Map<String, String>> setPassword(
            @RequestBody java.util.Map<String, String> request,
            Authentication authentication) {
        try {
            String newPassword = request.get("newPassword");
            if (newPassword == null || newPassword.length() < 6) {
                return ResponseEntity.badRequest()
                        .body(java.util.Map.of("message", "Le mot de passe doit contenir au moins 6 caractères."));
            }
            User user = userService.findByEmail(authentication.getName());
            userService.setPasswordDirect(user.getId(), newPassword);
            return ResponseEntity.ok(java.util.Map.of("message", "Mot de passe créé avec succès."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("message", "Erreur: " + e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<java.util.Map<String, String>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        // Pour le reset, on utilise aussi l'URL de base détectée. 
        // Si le frontend est sur un autre port, on pourrait avoir besoin de le configurer, 
        // mais utiliser l'hôte actuel est déjà un grand progrès par rapport à l'IP locale codée en dur.
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        
        passwordResetService.generatePasswordResetToken(request.getEmail(), baseUrl);
        return ResponseEntity
                .ok(java.util.Map.of("message", "Si l'email existe, un lien de réinitialisation vous a été envoyé."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<java.util.Map<String, String>> resetPassword(@RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(java.util.Map.of("message", "Votre mot de passe a été réinitialisé avec succès."));
    }

    @PostMapping("/change-password")
    public ResponseEntity<java.util.Map<String, String>> changePassword(
            @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        userService.changePassword(authentication.getName(), request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.ok(java.util.Map.of("message", "Mot de passe modifié avec succès."));
    }
}