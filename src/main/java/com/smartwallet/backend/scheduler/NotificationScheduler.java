package com.smartwallet.backend.scheduler;

import com.smartwallet.backend.model.Alerte;
import com.smartwallet.backend.model.Objectif;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.repository.AlerteRepository;
import com.smartwallet.backend.repository.DepenseRepository;
import com.smartwallet.backend.repository.ObjectifRepository;
import com.smartwallet.backend.service.FirebaseService;
import com.smartwallet.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {
    private final UserService userService;
    private final DepenseRepository depenseRepository;
    private final FirebaseService firebaseService;
    private final ObjectifRepository objectifRepository;
    private final AlerteRepository alerteRepository;

    // Daily reminder at 20:00 if no expense was recorded today
    @Scheduled(cron = "0 0 20 * * *")
    // "0 0 20 * * *"
    public void sendInactivityReminder() {
        log.info("Executing Inactivity Reminder Job...");
        List<User> users = userService.getAllUsers();
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);

        for (User user : users) {
            long count = depenseRepository.countByUserAndDateAfter(user, todayStart);
            if (count == 0) {
                firebaseService.sendPushNotification(user, "Rappel Quotidien",
                        "Vous n'avez pas encore enregistré de dépenses aujourd'hui. Pensez à le faire pour suivre votre budget !");
            }
        }
    }

    // Daily check at 08:00 for expired objectifs that haven't been reached
    @Scheduled(cron = "0 0 8 * * *")

    public void checkExpiredObjectifs() {
        log.info("Executing Expired Objectifs Job...");
        LocalDate today = LocalDate.now();
        List<Objectif> expiredObjectifs = objectifRepository.findExpiredUnachieved(today);
        log.info("Found {} expired unachieved objectifs for {}", expiredObjectifs.size(), today);

        for (Objectif objectif : expiredObjectifs) {
            User user = objectif.getUser();
            if (user == null)
                continue;

            String msg = "⏰ La date limite de votre objectif \"" + objectif.getDescription()
                    + "\" est dépassée. Montant restant : "
                    + objectif.getMontantCible().subtract(objectif.getMontantActuel()) + " à récolter.";

            Alerte alerte = new Alerte();
            alerte.setUser(user);
            alerte.setDate(LocalDateTime.now());
            alerte.setMessage(msg);
            alerte.setConditionVerifiee(false);
            alerteRepository.save(alerte);

            firebaseService.sendPushNotification(user, "⏰ Objectif non atteint", msg);
        }
    }
}
