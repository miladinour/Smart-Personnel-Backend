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
public class AuthController {
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticateAndGetToken(@RequestBody LoginRequest authRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(authRequest.getEmail(), authRequest.getMotDePasse()));
        
        String token = jwtService.generateToken(authRequest.getEmail());
        User user = userService.findByEmail(authRequest.getEmail());
        return ResponseEntity.ok(new AuthResponse(token, user.getId(), user.getEmail()));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> registerUser(@RequestBody User user) throws Exception {
        User savedUser = userService.register(user);
        String token = jwtService.generateToken(savedUser.getEmail());
        return ResponseEntity.ok(new AuthResponse(token, savedUser.getId(), savedUser.getEmail()));
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