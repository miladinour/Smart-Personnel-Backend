package com.smartwallet.backend.repository;

import com.smartwallet.backend.model.Categorie;
import com.smartwallet.backend.model.Depense;
import com.smartwallet.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DepenseRepository extends JpaRepository<Depense, Long> {
    List<Depense> findByUser(User user);
    List<Depense> findByUserAndDateBetween(User user, LocalDateTime start, LocalDateTime end);
    List<Depense> findByUserAndCategorieAndDateBetween(User user, Categorie categorie, LocalDateTime start, LocalDateTime end);
}
