package com.smartwallet.backend.controller;

import com.smartwallet.backend.dto.BudgetDTO;
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
@CrossOrigin("*")
public class BudgetController {

    private final BudgetService budgetService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<BudgetDTO>> getAllBudgets(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
             System.err.println(">>> BudgetController - ERROR: Authentication or name is null!");
             throw new RuntimeException("Authentification requise");
        }
        System.out.println(">>> BudgetController - Fetching budgets for user: " + authentication.getName());
        User user = userService.findByEmail(authentication.getName());
        if (user == null) {
            System.err.println(">>> BudgetController - ERROR: User not found for email: " + authentication.getName());
            throw new RuntimeException("Utilisateur non trouvé");
        }
        System.out.println(">>> BudgetController - User ID: " + user.getId());
        return ResponseEntity.ok(budgetService.getBudgetsByUser(user));
    }

        @PostMapping
    public ResponseEntity<BudgetDTO> createBudget(@RequestBody BudgetDTO budgetDTO, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(budgetService.createBudget(budgetDTO, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BudgetDTO> updateBudget(@PathVariable("id") Long id, @RequestBody BudgetDTO budgetDTO, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(budgetService.updateBudget(id, budgetDTO, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBudget(@PathVariable("id") Long id, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        budgetService.deleteBudget(id, user);
        return ResponseEntity.noContent().build();
    }
}
