package com.smartwallet.backend.service;

import com.smartwallet.backend.model.Categorie;
import com.smartwallet.backend.repository.CategorieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategorieService {

    private final CategorieRepository categorieRepository;

    public List<Categorie> getAllCategories() {
        return categorieRepository.findAll();
    }

    public Categorie createCategorie(Categorie categorie) {
        if (categorie.getType() != null) {
            categorie.setType(categorie.getType().toUpperCase());
        }
        if (categorieRepository.existsByNomIgnoreCaseAndUserIsNull(categorie.getNom()) ||
            (categorie.getUser() != null && categorieRepository.existsByNomIgnoreCaseAndUser(categorie.getNom(), categorie.getUser()))) {
            throw new RuntimeException("Catégorie existe déjà");
        }
        return categorieRepository.save(categorie);
    }

    public List<Categorie> getCategoriesByUser(com.smartwallet.backend.model.User user) {
        return deduplicate(categorieRepository.findByUserOrUserIsNull(user));
    }

    public List<Categorie> getCategoriesByUserAndType(com.smartwallet.backend.model.User user, String type) {
        String normalizedType = (type != null) ? type.toUpperCase() : null;
        return deduplicate(categorieRepository.findByUserOrUserIsNullAndType(user, normalizedType));
    }

    private List<Categorie> deduplicate(List<Categorie> categories) {
        java.util.Map<String, Categorie> uniqueCats = new java.util.LinkedHashMap<>();
        for (Categorie cat : categories) {
            String key = cat.getNom().toLowerCase().trim();
            Categorie existing = uniqueCats.get(key);
            if (existing == null || cat.getUser() != null) {
                uniqueCats.put(key, cat);
            }
        }
        return new java.util.ArrayList<>(uniqueCats.values());
    }

    public Categorie updateCategorie(Long id, Categorie categorieDetails) {
        Categorie categorie = categorieRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Catégorie non trouvée avec l'id: " + id));

        // Vérifier si le nouveau nom existe déjà pour cet utilisateur ou par défaut (si le nom a changé)
        if (!categorie.getNom().equalsIgnoreCase(categorieDetails.getNom())) {
            if (categorieRepository.existsByNomIgnoreCaseAndUserIsNull(categorieDetails.getNom()) ||
                (categorie.getUser() != null && categorieRepository.existsByNomIgnoreCaseAndUser(categorieDetails.getNom(), categorie.getUser()))) {
                throw new RuntimeException("Catégorie existe déjà");
            }
        }

        categorie.setNom(categorieDetails.getNom());
        if (categorieDetails.getType() != null) {
            categorie.setType(categorieDetails.getType().toUpperCase());
        }

        return categorieRepository.save(categorie);
    }

    public void deleteCategorie(Long id, com.smartwallet.backend.model.User user) {
        Categorie categorie = categorieRepository.findById(id)
                .filter(c -> c.getUser() != null && c.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Catégorie non trouvée ou non autorisée"));
        categorieRepository.delete(categorie);
    }
}
