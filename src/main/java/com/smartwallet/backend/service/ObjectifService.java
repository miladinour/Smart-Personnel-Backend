package com.smartwallet.backend.service;
import com.smartwallet.backend.model.Alerte;
import com.smartwallet.backend.model.Objectif;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.repository.AlerteRepository;
import com.smartwallet.backend.repository.ObjectifRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ObjectifService {

    private final ObjectifRepository objectifRepository;
    private final AlerteRepository alerteRepository;
    private final FirebaseService firebaseService;

    public List<Objectif> getObjectifsByUser(User user) {
        return objectifRepository.findByUser(user);
    }

    public Objectif createObjectif(Objectif objectif, User user) {
        System.out
                .println(">>> ObjectifService - Creating goal for user: " + (user != null ? user.getEmail() : "null"));
        if (objectif == null) {
            System.err.println(">>> ObjectifService - Goal object is null");
            throw new RuntimeException("L'objectif ne peut pas être null");
        }
        System.out.println(">>> ObjectifService - Goal details: " + objectif);

        if (user == null) {
            System.err.println(">>> ObjectifService - User is null");
            throw new RuntimeException("Utilisateur non authentifié ou introuvable");
        }

        if (objectif.getMontantCible() == null || objectif.getMontantCible().compareTo(BigDecimal.ZERO) <= 0) {
            System.err.println(">>> ObjectifService - Invalid target amount: " + objectif.getMontantCible());
            throw new RuntimeException("Le montant cible doit être supérieur à zéro");
        }
        
        objectif.setUser(user);
        if (objectif.getMontantActuel() == null) {
            objectif.setMontantActuel(BigDecimal.ZERO);
        }

        try {
            Objectif saved = objectifRepository.save(objectif);
            System.out.println(">>> ObjectifService - Goal saved successfully with ID: " + saved.getId());
            return saved;
        } catch (Exception e) {
            System.err.println(">>> ObjectifService - Error saving goal: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Erreur lors de l'enregistrement de l'objectif: " + e.getMessage());
        }
    }

    public Objectif updateObjectif(Long id, Objectif details, User user) {
        Objectif objectif = objectifRepository.findById(id)
                .filter(o -> o.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Objectif non trouvé"));

        boolean wasAtteint = objectif.isAtteint();

        objectif.setDescription(details.getDescription());
        objectif.setMontantCible(details.getMontantCible());
        objectif.setMontantActuel(details.getMontantActuel());
        objectif.setDateLimite(details.getDateLimite());

        if (objectif.getMontantActuel() != null && objectif.getMontantCible() != null) {
            objectif.setAtteint(objectif.getMontantActuel().compareTo(objectif.getMontantCible()) >= 0);
        }

        Objectif saved = objectifRepository.save(objectif);

        // Trigger notification only when newly reached (was not reached before)
        if (!wasAtteint && saved.isAtteint()) {
            String msg = "🎉 Bravo ! Votre objectif \"" + saved.getDescription() + "\" a été atteint !";
            // Save in-app notification (Alerte)
            Alerte alerte = new Alerte();
            alerte.setUser(user);
            alerte.setDate(LocalDateTime.now());
            alerte.setMessage(msg);
            alerte.setConditionVerifiee(false);
            alerteRepository.save(alerte);

            // Send FCM push notification
            firebaseService.sendPushNotification(user, "🏆 Objectif atteint !", msg);
        }

        return saved;
    }

    public void deleteObjectif(Long id, User user) {
        Objectif objectif = objectifRepository.findById(id)
                .filter(o -> o.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Objectif non trouvé"));
        objectifRepository.delete(objectif);
    }
}
