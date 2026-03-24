package com.smartwallet.backend.controller;

import com.smartwallet.backend.model.Budget;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.service.BudgetService;
import com.smartwallet.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<Budget>> getAllBudgets(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(budgetService.getBudgetsByUser(user));
    }

    @PostMapping
    public ResponseEntity<Budget> createBudget(@RequestBody Budget budget, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(budgetService.createBudget(budget, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBudget(@PathVariable("id") Long id, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        budgetService.deleteBudget(id, user);
        return ResponseEntity.noContent().build();
    }
}
