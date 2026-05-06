package com.smartwallet.backend.service;

import com.smartwallet.backend.dto.BudgetDTO;
import com.smartwallet.backend.dto.BudgetResponse;
import com.smartwallet.backend.model.Depense;
import com.smartwallet.backend.model.Budget;
import com.smartwallet.backend.model.Categorie;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.repository.BudgetRepository;
import com.smartwallet.backend.repository.CategorieRepository;
import com.smartwallet.backend.repository.DepenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategorieRepository categorieRepository;
    private final DepenseRepository depenseRepository;

    public List<BudgetDTO> getBudgetsByUser(User user) {
        if (user == null || user.getId() == null) {
            System.err.println(">>> BudgetService - ERROR: getBudgetsByUser called with null user or null user ID!");
            throw new RuntimeException("Utilisateur non authentifié ou introuvable");
        }
        return budgetRepository.findByUser(user).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public BudgetDTO createBudget(BudgetDTO budgetDTO, User user) {
        if (user == null || user.getId() == null) {
            System.err.println(">>> BudgetService - ERROR: createBudget called with null user or null user ID!");
            throw new RuntimeException("Utilisateur non authentifié ou introuvable");
        }
        String typeStr = budgetDTO.getType() != null ? budgetDTO.getType().toUpperCase() : "CATEGORIE";
        Budget.BudgetType type = Budget.BudgetType.valueOf(typeStr);
        
        Categorie categorie = null;
        if (type == Budget.BudgetType.CATEGORIE) {
            Long catId = budgetDTO.getCategorieId();
            if (catId == null) {
                 throw new RuntimeException("L'ID de la catégorie ne peut pas être nul pour un budget par catégorie");
            }
            categorie = categorieRepository.findById(catId)
                    .orElseThrow(() -> new RuntimeException("Catégorie non trouvée"));
        }

        Budget budget = new Budget();
        budget.setUser(user);
        budget.setCategorie(categorie);
        budget.setMontantLimite(budgetDTO.getMontantLimite());
        budget.setDateDebut(budgetDTO.getDateDebut());
        budget.setDateFin(budgetDTO.getDateFin());
        budget.setType(type);

        Budget savedBudget = budgetRepository.save(budget);
        return convertToDTO(savedBudget);
    }

    public BudgetDTO updateBudget(Long id, BudgetDTO budgetDTO, User user) {
        if (id == null) {
            throw new RuntimeException("L'ID du budget ne peut pas être nul");
        }
        Budget budget = budgetRepository.findById(id)
                .filter(b -> b.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Budget non trouvé"));

        if (budgetDTO.getMontantLimite() != null) {
            budget.setMontantLimite(budgetDTO.getMontantLimite());
        }
        if (budgetDTO.getDateDebut() != null) {
            budget.setDateDebut(budgetDTO.getDateDebut());
        }
        if (budgetDTO.getDateFin() != null) {
            budget.setDateFin(budgetDTO.getDateFin());
        }
        if (budgetDTO.getType() != null) {
            budget.setType(Budget.BudgetType.valueOf(budgetDTO.getType().toUpperCase()));
            if (budget.getType() == Budget.BudgetType.CATEGORIE && budgetDTO.getCategorieId() != null) {
                 Categorie cat = categorieRepository.findById(budgetDTO.getCategorieId())
                         .orElseThrow(() -> new RuntimeException("Catégorie non trouvée"));
                 budget.setCategorie(cat);
            } else if (budget.getType() == Budget.BudgetType.GLOBAL) {
                budget.setCategorie(null);
            }
        }

        Budget updatedBudget = budgetRepository.save(budget);
        return convertToDTO(updatedBudget);
    }

    public void deleteBudget(Long id, User user) {
        if (id == null) {
            throw new RuntimeException("L'ID du budget ne peut pas être nul");
        }
        Budget budget = budgetRepository.findById(id)
                .filter(b -> b.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Budget non trouvé"));
        budgetRepository.delete(budget);
    }
    private BudgetDTO convertToDTO(Budget budget) {
        BudgetDTO dto = new BudgetDTO();
        dto.setId(budget.getId());
        dto.setDateDebut(budget.getDateDebut());
        dto.setDateFin(budget.getDateFin());
        dto.setMontantLimite(budget.getMontantLimite());
        if (budget.getUser() != null) {
            dto.setUserId(budget.getUser().getId());
        }
        if (budget.getCategorie() != null) {
            dto.setCategorieId(budget.getCategorie().getId());
            dto.setCategorieNom(budget.getCategorie().getNom());
        } else {
            dto.setCategorieNom("Global");
        }

        // Calculate current usage
        if (budget.getUser() != null && budget.getDateDebut() != null && budget.getDateFin() != null) {
            LocalDateTime startDateTime = budget.getDateDebut().atStartOfDay();
            LocalDateTime endDateTime = budget.getDateFin().atTime(LocalTime.MAX);
            
            double totalSpent;
            if (budget.getType() == Budget.BudgetType.CATEGORIE && budget.getCategorie() != null) {
                List<Depense> depenses = depenseRepository.findByUserAndCategorieAndDateBetween(
                        budget.getUser(), budget.getCategorie(), startDateTime, endDateTime);
                totalSpent = depenses.stream().mapToDouble(d -> d.getMontant().doubleValue()).sum();
            } else {
                // GLOBAL - sum all expenses in period
                List<Depense> depenses = depenseRepository.findByUserAndDateBetween(
                        budget.getUser(), startDateTime, endDateTime);
                totalSpent = depenses.stream().mapToDouble(d -> d.getMontant().doubleValue()).sum();
            }
            dto.setCurrentAmount(totalSpent);
        }
        
        dto.setType(budget.getType() != null ? budget.getType().name() : "CATEGORIE");

        return dto;
    }
}
