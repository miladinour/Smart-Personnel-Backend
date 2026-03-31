package com.smartwallet.backend.repository;

import com.smartwallet.backend.model.Categorie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("SELECT c FROM Categorie c WHERE (c.user = :user OR c.user IS NULL) AND (c.type = :type OR :type IS NULL)")
    List<Categorie> findByUserOrUserIsNullAndType(@Param("user") User user, @Param("type") String type);

    @Query("SELECT c FROM Categorie c WHERE c.user = :user OR c.user IS NULL")
    List<Categorie> findByUserOrUserIsNull(@Param("user") User user);

    @Query("SELECT c FROM Categorie c WHERE LOWER(c.nom) = LOWER(:nom) AND (c.user = :user OR c.user IS NULL) ORDER BY c.user DESC")
    List<Categorie> findByNomAndUserOrUserIsNull(@Param("nom") String nom, @Param("user") User user);
}
