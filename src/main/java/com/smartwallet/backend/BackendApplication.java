package com.smartwallet.backend;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@Bean
	public CommandLineRunner fixDatabase(JdbcTemplate jdbcTemplate) {
		return args -> {
			System.out.println("=== DEBUT DE LA REPARATION DE LA BASE DE DONNEES ===");
			try {
				// 1. Ajouter les colonnes si elles n'existent pas
				jdbcTemplate.execute("ALTER TABLE utilisateur ADD COLUMN IF NOT EXISTS enabled BOOLEAN DEFAULT TRUE");
				jdbcTemplate.execute("ALTER TABLE utilisateur ADD COLUMN IF NOT EXISTS verification_token VARCHAR(255)");
				System.out.println("✓ Colonnes 'enabled' et 'verification_token' vérifiées/ajoutées.");

				// 2. Mettre à jour les utilisateurs existants
				jdbcTemplate.execute("UPDATE utilisateur SET enabled = TRUE WHERE enabled IS NULL");
				System.out.println("✓ Utilisateurs existants activés.");

				// 3. Supprimer les données dépendantes par EMAIL (plus fiable que par ID)
				String testEmail = "mejdoubabir272@gmail.com";
				System.out.println("... Tentative de nettoyage pour : " + testEmail);
				
				try {
					Integer userId = jdbcTemplate.queryForObject(
						"SELECT id FROM personne WHERE email = ?", Integer.class, testEmail);
					
					if (userId != null) {
						// Supprimer tout ce qui est lié à cet utilisateur par ordre de dépendance
						jdbcTemplate.execute("DELETE FROM dettes WHERE user_id = " + userId);
						jdbcTemplate.execute("DELETE FROM defis WHERE user_id = " + userId);
						jdbcTemplate.execute("DELETE FROM alertes WHERE user_id = " + userId);
						jdbcTemplate.execute("DELETE FROM budgets WHERE user_id = " + userId);
						jdbcTemplate.execute("DELETE FROM transactions WHERE user_id = " + userId);
						jdbcTemplate.execute("DELETE FROM objectifs WHERE user_id = " + userId);
						jdbcTemplate.execute("DELETE FROM categories WHERE user_id = " + userId);
						jdbcTemplate.execute("DELETE FROM password_reset_token WHERE user_id = " + userId);
						
						// Enfin supprimer l'utilisateur et la personne
						jdbcTemplate.execute("DELETE FROM utilisateur WHERE id = " + userId);
						jdbcTemplate.execute("DELETE FROM personne WHERE id = " + userId);
						System.out.println("✓ Nettoyage complet terminé pour l'ancien compte " + testEmail);
					}
				} catch (org.springframework.dao.EmptyResultDataAccessException e) {
					System.out.println("i Aucun ancien compte à nettoyer pour " + testEmail);
				}

				System.out.println("=== REPARATION TERMINEE AVEC SUCCES ===");
			} catch (Exception e) {
				System.err.println("!!! ERREUR LORS DE LA REPARATION : " + e.getMessage());
			}
		};
	}

}
