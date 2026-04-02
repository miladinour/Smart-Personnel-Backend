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
    Optional<Categorie> findByNomAndUser(@org.springframework.data.repository.query.Param("nom") String nom,
            @org.springframework.data.repository.query.Param("user") User user);

    boolean existsByNomIgnoreCaseAndUser(@org.springframework.data.repository.query.Param("nom") String nom,
            @org.springframework.data.repository.query.Param("user") User user);

    @Query("SELECT c FROM Categorie c WHERE c.nom = :nom AND (c.user = :user OR c.user IS NULL)")
    List<Categorie> findByNomAndUserOrUserIsNull(@org.springframework.data.repository.query.Param("nom") String nom, @org.springframework.data.repository.query.Param("user") User user);

    boolean existsByNomIgnoreCaseAndUserIsNull(@org.springframework.data.repository.query.Param("nom") String nom);

    List<Categorie> findByUser(@org.springframework.data.repository.query.Param("user") User user);

    List<Categorie> findByUserOrUserIsNull(@org.springframework.data.repository.query.Param("user") User user);

    List<Categorie> findByUserAndType(
            @org.springframework.data.repository.query.Param("user") User user,
            @org.springframework.data.repository.query.Param("type") String type);

    List<Categorie> findByUserAndTypeIgnoreCaseOrUserIsNullAndTypeIgnoreCase(
            @org.springframework.data.repository.query.Param("user") User user,
            @org.springframework.data.repository.query.Param("type") String type,
            @org.springframework.data.repository.query.Param("type2") String type2);
}
