package com.smartwallet.backend.service;

import com.smartwallet.backend.model.User;
import com.smartwallet.backend.model.Depense;
import com.smartwallet.backend.model.Categorie;
import com.smartwallet.backend.repository.DepenseRepository;
import com.smartwallet.backend.repository.CategorieRepository;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DepenseService {

    private final DepenseRepository depenseRepository;
    private final CategorieRepository categorieRepository;
    private final AiCategorizationService aiCategorizationService;
    private final UserService userService;
    private final DefiService defiService;

    public List<Depense> getDepensesByUser(User user, LocalDateTime start, LocalDateTime end, Long categoryId) {
        return depenseRepository.findByUser(user).stream()
                .filter(d -> (start == null || !d.getDate().isBefore(start)))
                .filter(d -> (end == null || !d.getDate().isAfter(end)))
                .filter(d -> (categoryId == null || d.getCategorie().getId().equals(categoryId)))
                .toList();
    }

    public Depense getDepenseById(Long id) {
        return depenseRepository.findById(id).orElseThrow(() -> new RuntimeException("Dépense non trouvée"));
    }

    public Depense createDepense(Depense depense, User user) {
        depense.setUser(user);
        if (depense.getDate() == null) {
            depense.setDate(LocalDateTime.now());
        }
        resolveOrCreateCategorie(depense, user);
        Depense savedDepense = depenseRepository.save(depense);
        userService.updateSolde(user.getId(), depense.getMontant().negate());
        
        // --- DEFI CHECK ---
        if (depense.getCategorie() != null) {
            defiService.checkViolation(user, depense.getCategorie().getNom());
        }
        
        return savedDepense;
    }

    public Depense updateDepense(Long id, Depense depenseDetails, User user) {
        Depense depense = depenseRepository.findById(id)
                .filter(d -> d.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Dépense non trouvée ou non autorisée"));

        java.math.BigDecimal oldMontant = depense.getMontant();
        java.math.BigDecimal newMontant = depenseDetails.getMontant();

        depense.setMontant(newMontant);
        depense.setDescription(depenseDetails.getDescription());
        // Removed: depense.setProjets(depenseDetails.getProjets()); // ✅ Update projets
        if (depenseDetails.getDate() != null) {
            depense.setDate(depenseDetails.getDate());
        }

        // Résolution de la catégorie pour l'update
        depense.setCategorie(depenseDetails.getCategorie());
        resolveOrCreateCategorie(depense, user);

        Depense updated = depenseRepository.save(depense);
        
        // Update balance: add back old amount (it was negative) and subtract new amount
        // Result = -(new - old) = old - new
        userService.updateSolde(user.getId(), oldMontant.subtract(newMontant));

        return updated;
    }

    private void resolveOrCreateCategorie(Depense depense, User user) {
        // If category is null or has "IA" placeholder name, use AI categorization
        if (depense.getCategorie() == null
                || (depense.getCategorie().getId() == null && "IA".equalsIgnoreCase(depense.getCategorie().getNom()))) {
            depense.setCategorie(aiCategorizationService.categorize(depense.getDescription(), "DEPENSE", user));
            return;
        }

        if (depense.getCategorie().getId() == null && depense.getCategorie().getNom() != null) {
            String nomCat = depense.getCategorie().getNom();
            Categorie cat = categorieRepository.findByNomAndUserOrUserIsNull(nomCat, user).stream()
                    .findFirst()
                    .orElseGet(() -> {
                        Categorie newCat = new Categorie(nomCat);
                        newCat.setUser(user);
                        newCat.setType("DEPENSE");
                        return categorieRepository.save(newCat);
                    });
            depense.setCategorie(cat);
        }
    }

    public void deleteDepense(Long id, User user) {
        Depense depense = depenseRepository.findById(id)
                .filter(d -> d.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Dépense non trouvée ou non autorisée"));
        
        java.math.BigDecimal amount = depense.getMontant();
        depenseRepository.delete(depense);
        userService.updateSolde(user.getId(), amount);
    }

    public BigDecimal getTotalDepenses(User user) {
        return depenseRepository.findByUser(user).stream()
                .map(Depense::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Map<String, BigDecimal> getDepensesByCategory(User user) {
        List<Depense> depenses = depenseRepository.findByUser(user);
        Map<String, BigDecimal> stats = new HashMap<>();
        for (Depense d : depenses) {
            String catName = d.getCategorie().getNom();
            stats.put(catName, stats.getOrDefault(catName, BigDecimal.ZERO).add(d.getMontant()));
        }
        return stats;
    }

    public BigDecimal getTotalDepensesForMonth(User user, int month, int year) {
        LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime end = start.plusMonths(1).minusNanos(1);
        return depenseRepository.findByUserAndDateBetween(user, start, end).stream()
                .map(Depense::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getAverageMonthlyExpenses(User user, int months) {
        LocalDateTime start = LocalDateTime.now().minusMonths(months).withDayOfMonth(1).withHour(0).withMinute(0);
        LocalDateTime end = LocalDateTime.now().withDayOfMonth(1).minusNanos(1);
        List<Depense> depenses = depenseRepository.findByUserAndDateBetween(user, start, end);
        if (depenses.isEmpty()) return BigDecimal.ZERO;
        
        BigDecimal total = depenses.stream()
                .map(Depense::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(new BigDecimal(months), 2, java.math.RoundingMode.HALF_UP);
    }
}
