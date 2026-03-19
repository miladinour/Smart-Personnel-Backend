package com.smartwallet.backend.controller;

import com.smartwallet.backend.model.Depense;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.service.DepenseService;
import com.smartwallet.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/depenses")
@RequiredArgsConstructor
public class DepenseController {

    private final DepenseService depenseService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<Depense>> getAllDepenses(
            @RequestParam(name = "start", required = false) LocalDateTime start,
            @RequestParam(name = "end", required = false) LocalDateTime end,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(depenseService.getDepensesByUser(user, start, end, categoryId));
    }

    @PostMapping
    public ResponseEntity<Depense> createDepense(@RequestBody Depense depense, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(depenseService.createDepense(depense, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Depense> updateDepense(@PathVariable("id") Long id, @RequestBody Depense depense,
            Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(depenseService.updateDepense(id, depense, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteDepense(@PathVariable("id") Long id,
            Authentication authentication) {
        System.out.println(
                ">>> DepenseController - DELETE request for id: " + id + " by user: " + authentication.getName());
        User user = userService.findByEmail(authentication.getName());
        try {
            depenseService.deleteDepense(id, user);
            System.out.println(">>> DepenseController - DELETE success for id: " + id);
        } catch (Exception e) {
            System.out.println(">>> DepenseController - DELETE error for id: " + id + " : " + e.getMessage());
            throw e;
        }
        Map<String, String> response = new HashMap<>();
        response.put("message", "transaction supprimer avec succes");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/total")
    public ResponseEntity<BigDecimal> getTotalDepenses(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(depenseService.getTotalDepenses(user));
    }

    @GetMapping("/user/{userId}/total")
    public ResponseEntity<BigDecimal> getTotalDepensesByUserId(@PathVariable("userId") Long userId) {
        User user = userService.findById(userId);
        return ResponseEntity.ok(depenseService.getTotalDepenses(user));
    }

    @GetMapping("/stats/category")
    public ResponseEntity<Map<String, BigDecimal>> getDepensesByCategory(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(depenseService.getDepensesByCategory(user));
    }
}
