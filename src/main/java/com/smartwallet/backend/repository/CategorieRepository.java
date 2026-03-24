package com.smartwallet.backend.repository;

import com.smartwallet.backend.model.Categorie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.smartwallet.backend.model.User;
import java.util.List;
import java.util.Optional;

@Repository
public interface CategorieRepository extends JpaRepository<Categorie, Long> {
    Optional<Categorie> findByNomAndUser(String nom, User user);
    
    boolean existsByNomIgnoreCaseAndUser(String nom, User user);
    
    boolean existsByNomIgnoreCaseAndUserIsNull(String nom);

    List<Categorie> findByUser(User user);

    List<Categorie> findByUserAndType(User user, String type);
}
