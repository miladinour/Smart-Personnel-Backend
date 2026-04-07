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
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional
    public void generatePasswordResetToken(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);

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

            // Send email
            String resetLink = frontendUrl + "/reset-password?token=" + token;
            String emailBody = "Vous avez demandé la réinitialisation de votre mot de passe.\n\n"
                    + "Veuillez cliquer sur le lien ci-dessous pour le réinitialiser :\n"
                    + resetLink + "\n\n"
                    + "Ce lien expirera dans 1 heure.\n\n"
                    + "Si vous n'avez pas fait cette demande, veuillez ignorer cet e-mail.";

            emailService.sendEmail(user.getEmail(), "Réinitialisation de votre mot de passe", emailBody);
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
