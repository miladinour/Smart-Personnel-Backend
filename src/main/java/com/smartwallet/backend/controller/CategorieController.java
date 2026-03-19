package com.smartwallet.backend.controller;

import com.smartwallet.backend.model.Categorie;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.service.CategorieService;
import com.smartwallet.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategorieController {

    private final CategorieService categorieService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<Categorie>> getAllCategories(
            @RequestParam(name = "type", required = false) String type,
            Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        if (type != null) {
            return ResponseEntity.ok(categorieService.getCategoriesByUserAndType(user, type));
        }
        return ResponseEntity.ok(categorieService.getCategoriesByUser(user));
    }

    @PostMapping
    public ResponseEntity<Categorie> createCategorie(@RequestBody Categorie categorie, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        categorie.setUser(user);
        return ResponseEntity.ok(categorieService.createCategorie(categorie));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Categorie> updateCategorie(@PathVariable("id") Long id,
            @RequestBody Categorie categorieDetails,
            Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        // On s'assure que la catégorie appartient bien à l'utilisateur
        categorieDetails.setUser(user);
        return ResponseEntity.ok(categorieService.updateCategorie(id, categorieDetails));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategorie(@PathVariable("id") Long id, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        categorieService.deleteCategorie(id, user);
        return ResponseEntity.noContent().build();
    }
}
