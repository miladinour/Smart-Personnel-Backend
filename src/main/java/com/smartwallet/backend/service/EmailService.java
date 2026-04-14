package com.smartwallet.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
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

    @Async
    public void sendEmail(String to, String subject, String body) {
        log.info("📧 [ASYNC] Préparation de l'envoi d'email à : {}", to);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("mejdoubabir272@gmail.com");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            javaMailSender.send(message);
            log.info("✅ Email envoyé avec succès à : {}", to);
        } catch (Exception e) {
            log.error("❌ Échec de l'envoi de l'email à {} : {}", to, e.getMessage());
        }
    }

    @Async
    public void sendVerificationEmail(User user, String token, String serverUrl) {
        log.info("📧 [ASYNC] Préparation de l'email de vérification pour : {}", user.getEmail());
        String verificationUrl = serverUrl + "/api/auth/verify?token=" + token;
        String subject = "Vérifiez votre compte Smart Wallet";

        String htmlContent = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 10px;'>"
                + "<div style='text-align: center; margin-bottom: 30px;'>" 
                + "<h1 style='color: #00BFA5; margin: 0;'>Smart Wallet</h1>" 
                + "<p style='color: #666; font-size: 14px;'>Votre gestionnaire financier premium</p>" 
                + "</div>" 
                + "<div style='background-color: #f9f9f9; padding: 25px; border-radius: 8px;'>" 
                + "<h2>Bonjour " + user.getPrenom() + ",</h2>" 
                + "<p>Bienvenue chez Smart Wallet ! Pour activer votre compte et commencer à gérer vos finances intelligemment, merci de confirmer votre adresse email.</p>"
                + "<div style='text-align: center; margin: 30px 0;'>" 
                + "<a href='" + verificationUrl
                + "' style='background: linear-gradient(to right, #00BFA5, #00796B); color: white; padding: 15px 30px; text-decoration: none; border-radius: 5px; font-weight: bold; font-size: 16px; box-shadow: 0 4px 10px rgba(0,191,165,0.3); font-family: sans-serif;'>C'est moi, activer mon compte</a>"
                + "</div>" 
                + "<p style='color: #888; font-size: 12px;'>Si vous n'avez pas créé de compte sur Smart Wallet, vous pouvez ignorer cet email.</p>"
                + "</div>" 
                + "</div>";

        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
            helper.setFrom("mejdoubabir272@gmail.com");
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            javaMailSender.send(mimeMessage);
            log.info("✅ Email HTML de vérification envoyé à : {}", user.getEmail());
        } catch (Exception e) {
            log.error("❌ Erreur d'envoi d'email HTML à {} : {}", user.getEmail(), e.getMessage());
        }
    }
}
