package com.smartwallet.backend.service;

import com.smartwallet.backend.model.User;
import com.smartwallet.backend.model.Categorie;
import com.smartwallet.backend.repository.CategorieRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserInitializationService {

    private static final Logger log = LoggerFactory.getLogger(UserInitializationService.class);

    @Autowired
    private CategorieRepository categorieRepository;

    @Autowired
    private EmailService emailService;

    @Async
    @Transactional
    public void initializeNewUser(User user, String token, String serverUrl) {
        log.info("🚀 [ASYNC] Démarrage de l'initialisation pour : {}", user.getEmail());
        
        try {
            // 1. Création des catégories par défaut
            ensureDefaultCategories(user);
            
            // 2. Envoi de l'email de vérification
            emailService.sendVerificationEmail(user, token, serverUrl);
            
            log.info("✅ [ASYNC] Initialisation terminée pour {}", user.getEmail());
        } catch (Exception e) {
            log.error("❌ [ASYNC] Erreur lors de l'initialisation : {}", e.getMessage());
        }
    }

    private void ensureDefaultCategories(User user) {
        String[][] categories = {
                { "Alimentation", "DEPENSE" },
                { "Transport", "DEPENSE" },
                { "Loisirs", "DEPENSE" },
                { "Santé", "DEPENSE" },
                { "Shopping", "DEPENSE" },
                { "Logement", "DEPENSE" },
                { "Autre", "DEPENSE" },
                { "Salaire", "REVENU" },
                { "Cadeau", "REVENU" },
                { "Autre", "REVENU" }
        };

        for (String[] cat : categories) {
            try {
                if (categorieRepository.findByNomAndUser(cat[0], user).isEmpty()) {
                    Categorie c = new Categorie();
                    c.setNom(cat[0]);
                    c.setType(cat[1]);
                    c.setUser(user);
                    c.setSystemCategory(true);
                    categorieRepository.save(c);
                }
            } catch (Exception e) {
                log.warn("⚠️ Impossible de créer la catégorie {} : {}", cat[0], e.getMessage());
            }
        }
    }
}
