package com.smartwallet.backend.service;

import com.smartwallet.backend.model.Defi;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.repository.DefiRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefiService {

    private final DefiRepository defiRepository;

    public Defi acceptDefi(User user, String titre, String description, String category, int durationDays) {
        Defi defi = new Defi();
        defi.setUser(user);
        defi.setTitre(titre);
        defi.setDescription(description);
        defi.setCategorieCible(category);
        defi.setDateDebut(LocalDateTime.now());
        defi.setDateFin(LocalDateTime.now().plusDays(durationDays));
        defi.setActive(true);
        defi.setFailed(false);
        defi.setSuccess(false);
        return defiRepository.save(defi);
    }

    public List<Defi> getActiveDefis(User user) {
        // Clean up expired ones first
        List<Defi> active = defiRepository.findByUserAndActiveTrue(user);
        for (Defi d : active) {
            if (d.isExpired()) {
                d.setActive(false);
                d.setSuccess(true);
                defiRepository.save(d);
            }
        }
        return defiRepository.findByUserAndActiveTrue(user);
    }

    public void checkViolation(User user, String category) {
        List<Defi> active = defiRepository.findByUserAndActiveTrue(user);
        for (Defi d : active) {
            if (d.getCategorieCible().equalsIgnoreCase(category)) {
                d.setActive(false);
                d.setFailed(true);
                defiRepository.save(d);
                // Trigger a notification or alert here if needed
                System.out.println("DEFI ECHOUE: " + d.getTitre() + " pour l'utilisateur " + user.getNom());
            }
        }
    }

    public void deleteDefi(Long id, User user) {
        Defi defi = defiRepository.findById(id)
                .filter(d -> d.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Défi non trouvé ou non autorisé"));
        defiRepository.delete(defi);
    }
}
