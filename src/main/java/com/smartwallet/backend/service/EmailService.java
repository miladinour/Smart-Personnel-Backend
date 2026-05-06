package com.smartwallet.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Service;
import com.smartwallet.backend.model.User;

@Service
@Slf4j
public class EmailService {

    @Autowired
    private JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    public void sendEmail(String to, String subject, String body) {
        log.info("📧 [ASYNC] Préparation de l'envoi d'email à : {}", to);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            javaMailSender.send(message);
            log.info("✅ Email envoyé avec succès à : {}", to);
        } catch (Exception e) {
            log.error("❌ Échec de l'envoi de l'email à {} : ", to, e);
        }
    }

    @Async
    public void sendVerificationEmail(User user, String token, String serverUrl) {
        log.info("📧 [ASYNC] Préparation de l'email de vérification pour : {}", user.getEmail());

        String cleanServerUrl = serverUrl.trim();

        // ✅ Le bouton pointe vers /api/auth/verify (URL HTTPS normale)
        // Le backend active le compte et affiche une page de succès HTML
        String activationLink = cleanServerUrl + "/api/auth/verify"
                + "?token=" + token;

        String subject = "Vérifiez votre compte Smart Wallet";

        String htmlContent = "<div style='font-family: \"Segoe UI\", Tahoma, Geneva, Verdana, sans-serif;"
                + " max-width: 600px; margin: 0 auto; padding: 20px; color: #333;'>"

                + "<div style='text-align: center; padding: 20px 0;'>"
                + "<h1 style='color: #00BFA5; margin: 0; font-size: 28px; font-weight: 800;'>Smart Wallet</h1>"
                + "<p style='color: #7f8c8d; font-size: 14px; margin-top: 5px;'>Votre gestionnaire financier premium</p>"
                + "</div>"

                + "<div style='background-color: #ffffff; padding: 30px; border-radius: 16px;"
                + " border: 1px solid #f0f0f0; box-shadow: 0 4px 12px rgba(0,0,0,0.05); text-align: left;'>"

                + "<h2 style='color: #2c3e50; margin-top: 0;'>Bonjour " + user.getPrenom() + ",</h2>"

                + "<p style='line-height: 1.6; color: #525f7f; font-size: 16px;'>"
                + "Bienvenue chez <strong>Smart Wallet</strong> ! "
                + "Pour activer votre compte et commencer à gérer vos finances intelligemment, "
                + "merci de confirmer votre adresse email en cliquant sur le bouton ci-dessous :</p>"

                // ✅ Bouton avec lien HTTPS — cliquable partout (Gmail, Outlook, etc.)
                + "<div style='text-align: center; margin: 40px 0;'>"
                + "<a href='" + activationLink + "'"
                + " style='display: inline-block;"
                + " background: linear-gradient(135deg, #00BFA5 0%, #00796B 100%);"
                + " color: #ffffff; padding: 18px 35px; text-decoration: none;"
                + " border-radius: 12px; font-weight: bold; font-size: 16px;"
                + " box-shadow: 0 10px 20px rgba(0,191,165,0.2); white-space: nowrap;'>"
                + "C'est moi, activer mon compte</a>"
                + "</div>"

                + "<hr style='border: 0; border-top: 1px solid #eee; margin: 30px 0;'>"
                + "<p style='color: #bdc3c7; font-size: 12px; text-align: center;'>"
                + "Si vous n'avez pas créé de compte sur Smart Wallet, "
                + "vous pouvez ignorer cet email en toute sécurité.</p>"
                + "</div>"
                + "</div>";

        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            javaMailSender.send(mimeMessage);
            log.info("✅ Email HTML de vérification envoyé à : {}", user.getEmail());
        } catch (Exception e) {
            log.error("❌ Erreur d'envoi d'email HTML à {} : ", user.getEmail(), e);
        }
    }

    @Async
    public void sendPasswordResetEmail(User user, String token, String frontendUrl) {
        log.info("📧 [ASYNC] Préparation de l'email de réinitialisation pour : {}", user.getEmail());

        String resetLink = frontendUrl + "/reset-password?token=" + token;
        String subject = "Réinitialisation de votre mot de passe - Smart Wallet";

        String htmlContent = "<div style='font-family: \"Segoe UI\", Tahoma, Geneva, Verdana, sans-serif;"
                + " max-width: 600px; margin: 0 auto; padding: 20px; color: #333;'>"

                + "<div style='text-align: center; padding: 20px 0;'>"
                + "<h1 style='color: #00BFA5; margin: 0; font-size: 28px; font-weight: 800;'>Smart Wallet</h1>"
                + "<p style='color: #7f8c8d; font-size: 14px; margin-top: 5px;'>Gestion Sécurisée de vos Comptes</p>"
                + "</div>"

                + "<div style='background-color: #ffffff; padding: 30px; border-radius: 16px;"
                + " border: 1px solid #f0f0f0; box-shadow: 0 4px 12px rgba(0,0,0,0.05); text-align: left;'>"

                + "<h2 style='color: #2c3e50; margin-top: 0;'>Bonjour " + user.getPrenom() + ",</h2>"

                + "<p style='line-height: 1.6; color: #525f7f; font-size: 16px;'>"
                + "Vous avez demandé la réinitialisation de votre mot de passe. "
                + "Pas d'inquiétude, cliquez simplement sur le bouton ci-dessous pour en choisir un nouveau :</p>"

                + "<div style='text-align: center; margin: 40px 0;'>"
                + "<a href='" + resetLink + "'"
                + " style='display: inline-block;"
                + " background: linear-gradient(135deg, #00BFA5 0%, #00796B 100%);"
                + " color: #ffffff; padding: 18px 35px; text-decoration: none;"
                + " border-radius: 12px; font-weight: bold; font-size: 16px;"
                + " box-shadow: 0 10px 20px rgba(0,191,165,0.2); white-space: nowrap;'>"
                + "Réinitialiser mon mot de passe</a>"
                + "</div>"

                + "<p style='color: #e74c3c; font-size: 13px; text-align: center;'>"
                + "<strong>Note :</strong> Ce lien expirera dans 1 heure.</p>"

                + "<hr style='border: 0; border-top: 1px solid #eee; margin: 30px 0;'>"
                + "<p style='color: #bdc3c7; font-size: 12px; text-align: center;'>"
                + "Si vous n'êtes pas à l'origine de cette demande, "
                + "vous pouvez ignorer cet email. Votre mot de passe restera inchangé.</p>"
                + "</div>"
                + "</div>";

        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            javaMailSender.send(mimeMessage);
            log.info("✅ Email HTML de réinitialisation envoyé à : {}", user.getEmail());
        } catch (Exception e) {
            log.error("❌ Erreur d'envoi d'email HTML de réinitialisation à {} : ", user.getEmail(), e);
        }
    }
}