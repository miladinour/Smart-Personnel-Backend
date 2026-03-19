package com.smartwallet.backend.service;

import com.smartwallet.backend.model.Categorie;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.repository.CategorieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AiCategorizationService {

    private final CategorieRepository categorieRepository;

    private static final Map<String, String[]> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("Alimentation", new String[] { "restau", "burger", "pizza", "carrefour", "monoprix", "magasin",
                "food", "eat", "cafe", "nourriture", "courses", "fastfood" });
        KEYWORDS.put("Transport", new String[] { "uber", "bolt", "taxi", "essence", "car", "train", "bus", "parking",
                "carburant", "vol" });
        KEYWORDS.put("Loisirs", new String[] { "cinéma", "netflix", "ps5", "jeu", "sortie", "party", "club", "vacances",
                "sport", "gym" });
        KEYWORDS.put("Santé",
                new String[] { "pharmacie", "docteur", "hosto", "medecin", "dentiste", "clinique", "soin" });
        KEYWORDS.put("Shopping",
                new String[] { "habit", "vêtement", "zara", "h&m", "jouet", "achat", "mall", "decathlon" });
        KEYWORDS.put("Logement",
                new String[] { "loyer", "électricité", "eau", "gaz", "internet", "assurance", "meuble", "travaux" });
        KEYWORDS.put("Salaire", new String[] { "salaire", "vir", "virement", "bonus", "paye", "rémunération" });
    }

    public Categorie categorize(String description, String type, User user) {
        if (description == null || description.isEmpty()) {
            return getOrCreateOtherCategory(user, type);
        }

        String descLower = description.toLowerCase();

        for (Map.Entry<String, String[]> entry : KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (descLower.contains(keyword)) {
                    return getOrCreateCategory(entry.getKey(), type, user);
                }
            }
        }

        return getOrCreateOtherCategory(user, type);
    }

    private Categorie getOrCreateCategory(String nom, String type, User user) {
        Optional<Categorie> existing = categorieRepository.findByNomAndUser(nom, user);
        if (existing.isPresent()) {
            return existing.get();
        }
        Categorie newCat = new Categorie();
        newCat.setNom(nom);
        newCat.setType(type);
        newCat.setUser(user);
        return categorieRepository.save(newCat);
    }

    private Categorie getOrCreateOtherCategory(User user, String type) {
        return getOrCreateCategory("Autre", type, user);
    }
}
