package com.smartwallet.backend.scheduler;

import com.smartwallet.backend.model.User;
import com.smartwallet.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResetScheduler {

    private final UserService userService;

    /**
     * Chaque jour à minuit, on vérifie si certains utilisateurs doivent être réinitialisés.
     * Cron : "0 0 0 * * *"
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void performScheduledResets() {
        log.info("Démarrage de la vérification des réinitialisations programmées...");
        List<User> users = userService.getAllUsers();
        LocalDateTime now = LocalDateTime.now();

        for (User user : users) {
            String interval = user.getResetInterval();
            if (interval == null || interval.equals("NONE") || interval.equals("NEVER")) {
                continue;
            }

            LocalDateTime lastReset = user.getLastResetDate();
            if (lastReset == null) {
                // Si jamais réinitialisé, on prend la date de création
                lastReset = user.getDateCreation();
            }

            boolean shouldReset = false;

            switch (interval.toUpperCase()) {
                case "MONTHLY":
                case "MENSUEL":
                    if (now.getMonth() != lastReset.getMonth() || now.getYear() != lastReset.getYear()) {
                        // On réinitialise au début du nouveau mois
                        shouldReset = now.getDayOfMonth() == 1;
                    }
                    break;
                case "SEMIANNUAL":
                case "SEMESTRIEL":
                    // Tous les 6 mois
                    if (now.minusMonths(6).isAfter(lastReset) || now.minusMonths(6).isEqual(lastReset)) {
                        shouldReset = true;
                    }
                    break;
                case "ANNUAL":
                case "ANNUEL":
                    if (now.getYear() > lastReset.getYear()) {
                        shouldReset = now.getDayOfYear() == 1;
                    }
                    break;
            }

            if (shouldReset) {
                log.info("Réinitialisation programmée ({}) pour l'utilisateur : {}", interval, user.getEmail());
                try {
                    userService.resetUserData(user.getId());
                } catch (Exception e) {
                    log.error("Erreur lors de la réinitialisation de l'utilisateur {} : {}", user.getEmail(), e.getMessage());
                }
            }
        }
        log.info("Fin de la vérification des réinitialisations programmées.");
    }
}
