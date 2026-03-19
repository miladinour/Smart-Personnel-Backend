package com.smartwallet.backend.service;

import com.smartwallet.backend.model.Categorie;
import com.smartwallet.backend.repository.CategorieRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryMigrationService {

    private final CategorieRepository categorieRepository;

    @PostConstruct
    public void migrateCategories() {
        List<String> systemNames = Arrays.asList(
            "Alimentation", "Transport", "Loisirs", "Santé", 
            "Shopping", "Logement", "Autre", "Salaire", "Cadeau"
        );

        List<Categorie> allCategories = categorieRepository.findAll();
        for (Categorie cat : allCategories) {
            boolean shouldBeDefault = systemNames.contains(cat.getNom());
            boolean isSystem = Boolean.TRUE.equals(cat.isSystemCategory());
            if (isSystem != shouldBeDefault) {
                cat.setSystemCategory(shouldBeDefault);
                categorieRepository.save(cat);
            }
        }
    }
}
