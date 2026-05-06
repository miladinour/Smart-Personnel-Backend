package com.smartwallet.backend.service;

import com.smartwallet.backend.model.PasswordResetToken;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.repository.PasswordResetTokenRepository;
import com.smartwallet.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final String frontendUrl;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            @Value("${app.frontend.url}") String frontendUrl) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public void generatePasswordResetToken(String email, String currentFrontendUrl) {
        String normalizedEmail = email != null ? email.toLowerCase().trim() : null;
        Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            // Delete any existing token for this user

            passwordResetTokenRepository.deleteByUser(user);
            passwordResetTokenRepository.flush();

            // Generate a new token
            String token = UUID.randomUUID().toString();
            PasswordResetToken passwordResetToken = new PasswordResetToken(token, user);

            // Save token to database
            passwordResetTokenRepository.save(passwordResetToken);

            String finalFrontendUrl = (currentFrontendUrl != null && !currentFrontendUrl.isEmpty()) 
                                    ? currentFrontendUrl 
                                    : frontendUrl;
            
            emailService.sendPasswordResetEmail(user, token, finalFrontendUrl);
        }
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide ou introuvable."));

        if (resetToken.isExpired()) {
            passwordResetTokenRepository.delete(resetToken);
            throw new RuntimeException("Le token a expiré.");
        }

        User user = resetToken.getUser();
        user.setMotDePasse(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Delete token after successful reset
        passwordResetTokenRepository.delete(resetToken);
    }
}
