package com.smartwallet.backend.service;

import com.smartwallet.backend.model.User;
import com.smartwallet.backend.model.Revenu;
import com.smartwallet.backend.repository.RevenuRepository;
import com.smartwallet.backend.model.Categorie;
import com.smartwallet.backend.repository.CategorieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RevenuService {

    private final RevenuRepository revenuRepository;
    private final CategorieRepository categorieRepository;
    private final AiCategorizationService aiCategorizationService;

    public List<Revenu> getRevenusByUser(User user, LocalDateTime start, LocalDateTime end, Long categoryId) {
        return revenuRepository.findByUser(user).stream()
                .filter(r -> r.getDate() != null)
                .filter(r -> (start == null || !r.getDate().isBefore(start)))
                .filter(r -> (end == null || !r.getDate().isAfter(end)))
                .filter(r -> (categoryId == null || (r.getCategorie() != null && r.getCategorie().getId() != null && r.getCategorie().getId().equals(categoryId))))
                .toList();
    }

    // Removed getRevenuById(Long id)

    public Revenu createRevenu(Revenu revenu, User user) {
        revenu.setUser(user);
        if (revenu.getDate() == null) {
            revenu.setDate(LocalDateTime.now());
        }
        resolveOrCreateCategorie(revenu, user);
        return revenuRepository.save(revenu);
    }

    public Revenu updateRevenu(Long id, Revenu revenuDetails, User user) {
        Revenu revenu = revenuRepository.findById(id)
                .filter(r -> r.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Revenu non trouvé ou non autorisé"));

        revenu.setMontant(revenuDetails.getMontant());
        revenu.setDescription(revenuDetails.getDescription());
        revenu.setRecurring(revenuDetails.isRecurring());
        if (revenuDetails.getDate() != null) {
            revenu.setDate(revenuDetails.getDate());
        }

        revenu.setCategorie(revenuDetails.getCategorie());
        resolveOrCreateCategorie(revenu, user);

        return revenuRepository.save(revenu);
    }

    private void resolveOrCreateCategorie(Revenu revenu, User user) {
        // If category is null or has "IA" placeholder name, use AI categorization
        if (revenu.getCategorie() == null
                || (revenu.getCategorie().getId() == null && "IA".equalsIgnoreCase(revenu.getCategorie().getNom()))) {
            revenu.setCategorie(aiCategorizationService.categorize(revenu.getDescription(), "REVENU", user));
            return;
        }

        if (revenu.getCategorie().getId() == null && revenu.getCategorie().getNom() != null) {
            String nomCat = revenu.getCategorie().getNom();
            Categorie cat = categorieRepository.findByNomAndUser(nomCat, user)
                    .orElseGet(() -> {
                        Categorie newCat = new Categorie(nomCat);
                        newCat.setUser(user);
                        newCat.setType("REVENU");
                        return categorieRepository.save(newCat);
                    });
            revenu.setCategorie(cat);
        }
    }

    public void deleteRevenu(Long id, User user) {
        Revenu revenu = revenuRepository.findById(id)
                .filter(r -> r.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Revenu non trouvé ou non autorisé"));
        revenuRepository.delete(revenu);
    }

    public BigDecimal getTotalRevenus(User user) {
        return revenuRepository.findByUser(user).stream()
                .map(Revenu::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
