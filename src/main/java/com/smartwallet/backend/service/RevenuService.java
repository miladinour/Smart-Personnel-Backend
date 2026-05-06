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

    private final UserService userService;
    private final RevenuRepository revenuRepository;
    private final CategorieRepository categorieRepository;
    private final AiCategorizationService aiCategorizationService;

    public List<Revenu> getRevenusByUser(User user, LocalDateTime start, LocalDateTime end, Long categoryId) {
        return revenuRepository.findByUserOrderByDateDescIdDesc(user).stream()
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
        Revenu savedRevenu = revenuRepository.save(revenu);
        userService.updateSolde(user.getId(), revenu.getMontant());
        return savedRevenu;
    }

    public Revenu updateRevenu(Long id, Revenu revenuDetails, User user) {
        Revenu revenu = revenuRepository.findById(id)
                .filter(r -> r.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Revenu non trouvé ou non autorisé"));

        java.math.BigDecimal oldMontant = revenu.getMontant();
        java.math.BigDecimal newMontant = revenuDetails.getMontant();

        revenu.setMontant(newMontant);
        revenu.setDescription(revenuDetails.getDescription());
        revenu.setRecurring(revenuDetails.isRecurring());
        if (revenuDetails.getDate() != null) {
            revenu.setDate(revenuDetails.getDate());
        }

        revenu.setCategorie(revenuDetails.getCategorie());
        resolveOrCreateCategorie(revenu, user);

        Revenu updated = revenuRepository.save(revenu);
        
        // Update balance: subtract old amount and add new amount
        // Result = new - old
        userService.updateSolde(user.getId(), newMontant.subtract(oldMontant));

        return updated;
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
            Categorie cat = categorieRepository.findByNomAndUserOrUserIsNull(nomCat, user).stream()
                    .findFirst()
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
        java.math.BigDecimal amount = revenu.getMontant();
        revenuRepository.delete(revenu);
        userService.updateSolde(user.getId(), amount.negate());
    }

    public BigDecimal getTotalRevenus(User user) {
        return revenuRepository.findByUser(user).stream()
                .map(Revenu::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalRevenuForMonth(User user, int month, int year) {
        LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime end = start.plusMonths(1).minusNanos(1);
        return revenuRepository.findByUserAndDateBetween(user, start, end).stream()
                .map(Revenu::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getAverageMonthlyIncome(User user, int months) {
        LocalDateTime start = LocalDateTime.now().minusMonths(months).withDayOfMonth(1).withHour(0).withMinute(0);
        LocalDateTime end = LocalDateTime.now().withDayOfMonth(1).minusNanos(1);
        List<Revenu> revenus = revenuRepository.findByUserAndDateBetween(user, start, end);
        if (revenus.isEmpty()) return BigDecimal.ZERO;
        
        BigDecimal total = revenus.stream()
                .map(Revenu::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(new BigDecimal(months), 2, java.math.RoundingMode.HALF_UP);
    }
}
