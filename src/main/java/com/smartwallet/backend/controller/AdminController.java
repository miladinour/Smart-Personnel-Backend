package com.smartwallet.backend.controller;

import com.smartwallet.backend.model.User;
import com.smartwallet.backend.service.UserService;
import com.smartwallet.backend.repository.AdminRepository;
import com.smartwallet.backend.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AdminController {

    private final UserService userService;
    private final AdminRepository adminRepository;
    private final TransactionRepository transactionRepository;

    private boolean isAdmin(Authentication authentication) {
        return adminRepository.findByEmail(authentication.getName()).isPresent();
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body("Accès refusé : Réservé aux administrateurs.");
        }
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<?> updateUserStatus(@PathVariable Long id, @RequestParam boolean enabled, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body("Accès refusé.");
        }
        userService.updateUserStatus(id, enabled);
        return ResponseEntity.ok(Map.of("message", "Statut mis à jour avec succès."));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getGlobalStats(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body("Accès refusé.");
        }
        
        List<User> users = userService.getAllUsers();
        long transactionCount = transactionRepository.count();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", users.size());
        stats.put("activeUsers", users.stream().filter(User::isEnabled).count());
        stats.put("totalTransactions", transactionCount);
        
        return ResponseEntity.ok(stats);
    }
}
