package com.smartwallet.backend.controller;

import com.smartwallet.backend.model.Revenu;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.service.RevenuService;
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
@RequestMapping("/api/revenus")
@RequiredArgsConstructor
@CrossOrigin("*")
public class RevenuController {

    private final RevenuService revenuService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<Revenu>> getAllRevenus(
            @RequestParam(name = "start", required = false) LocalDateTime start,
            @RequestParam(name = "end", required = false) LocalDateTime end,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(revenuService.getRevenusByUser(user, start, end, categoryId));
    }

    @PostMapping
    public ResponseEntity<Revenu> createRevenu(@RequestBody Revenu revenu, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(revenuService.createRevenu(revenu, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Revenu> updateRevenu(@PathVariable("id") Long id, @RequestBody Revenu revenu,
            Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(revenuService.updateRevenu(id, revenu, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteRevenu(@PathVariable("id") Long id,
            Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        revenuService.deleteRevenu(id, user);
        Map<String, String> response = new HashMap<>();
        response.put("message", "transaction supprimer avec succes");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/total")
    public ResponseEntity<BigDecimal> getTotalRevenus(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(revenuService.getTotalRevenus(user));
    }
}
