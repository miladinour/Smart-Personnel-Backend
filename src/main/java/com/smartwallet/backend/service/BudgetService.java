package com.smartwallet.backend.service;

import com.smartwallet.backend.model.Budget;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.repository.BudgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;

    public List<Budget> getBudgetsByUser(User user) {
        return budgetRepository.findByUser(user);
    }

    public Budget createBudget(Budget budget, User user) {
        budget.setUser(user);
        return budgetRepository.save(budget);
    }

    public void deleteBudget(Long id, User user) {
        Budget budget = budgetRepository.findById(id)
                .filter(b -> b.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Budget non trouvé"));
        budgetRepository.delete(budget);
    }
}
