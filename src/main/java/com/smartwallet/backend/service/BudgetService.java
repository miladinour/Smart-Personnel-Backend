package com.smartwallet.backend.service;

import com.smartwallet.backend.dto.BudgetResponse;
import com.smartwallet.backend.model.Depense;
import com.smartwallet.backend.model.Budget;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.repository.BudgetRepository;
import com.smartwallet.backend.repository.DepenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final DepenseRepository depenseRepository;

    public List<BudgetResponse> getBudgetsByUser(User user) {
        return budgetRepository.findByUser(user).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public BudgetResponse createBudget(Budget budget, User user) {
        budget.setUser(user);
        return mapToResponse(budgetRepository.save(budget));
    }

    public void deleteBudget(Long id, User user) {
        Budget budget = budgetRepository.findById(id)
                .filter(b -> b.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Budget non trouvé"));
        budgetRepository.delete(budget);
    }

    private BudgetResponse mapToResponse(Budget budget) {
        BigDecimal currentAmount = BigDecimal.ZERO;
        if (budget.getType() == Budget.BudgetType.GLOBAL) {
            currentAmount = depenseRepository.findByUserAndDateBetween(
                    budget.getUser(), 
                    budget.getDateDebut().atStartOfDay(), 
                    budget.getDateFin().atTime(23, 59, 59)
            ).stream()
            .map(Depense::getMontant)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else if (budget.getType() == Budget.BudgetType.CATEGORIE && budget.getCategorie() != null) {
            currentAmount = depenseRepository.findByUserAndCategorieAndDateBetween(
                    budget.getUser(), 
                    budget.getCategorie(),
                    budget.getDateDebut().atStartOfDay(), 
                    budget.getDateFin().atTime(23, 59, 59)
            ).stream()
            .map(Depense::getMontant)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        return BudgetResponse.builder()
                .id(budget.getId())
                .dateDebut(budget.getDateDebut())
                .dateFin(budget.getDateFin())
                .montantLimite(budget.getMontantLimite())
                .currentAmount(currentAmount)
                .categorieId(budget.getCategorie() != null ? budget.getCategorie().getId() : null)
                .categorieNom(budget.getCategorie() != null ? budget.getCategorie().getNom() : "Global")
                .userId(budget.getUser().getId())
                .type(budget.getType().name())
                .build();
    }
}
