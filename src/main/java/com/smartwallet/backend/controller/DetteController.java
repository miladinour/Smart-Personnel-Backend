package com.smartwallet.backend.controller;

import com.smartwallet.backend.model.Dette;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.service.DetteService;
import com.smartwallet.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dettes")
@RequiredArgsConstructor
@CrossOrigin("*")
public class DetteController {

    private final DetteService detteService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<Dette>> getDettes(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(detteService.getDettesByUser(user));
    }

    @GetMapping("/active")
    public ResponseEntity<List<Dette>> getActiveDettes(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(detteService.getActiveDettes(user));
    }

    @PostMapping
    public ResponseEntity<Dette> createDette(@RequestBody Dette dette, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        dette.setUser(user);
        return ResponseEntity.ok(detteService.saveDette(dette));
    }

    @PutMapping("/{id}/paye")
    public ResponseEntity<Dette> markAsPaye(@PathVariable Long id) {
        Dette updated = detteService.markAsPaye(id);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDette(@PathVariable Long id) {
        detteService.deleteDette(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/all")
    public ResponseEntity<Void> deleteAll(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        detteService.deleteAllByUser(user);
        return ResponseEntity.ok().build();
    }
}
