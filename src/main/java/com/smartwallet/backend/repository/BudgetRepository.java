package com.smartwallet.backend.repository;

import com.smartwallet.backend.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import com.smartwallet.backend.model.User;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByUser(@org.springframework.data.repository.query.Param("user") User user);
    
    @org.springframework.data.jpa.repository.Query("SELECT b FROM Budget b WHERE b.user = :user AND b.categorie.id = :categorieId AND b.dateDebut <= :date AND b.dateFin >= :date")
    java.util.Optional<Budget> findActiveBudgetForCategory(@org.springframework.data.repository.query.Param("user") User user, @org.springframework.data.repository.query.Param("categorieId") Long categorieId, @org.springframework.data.repository.query.Param("date") java.time.LocalDate date);

    @org.springframework.transaction.annotation.Transactional
    void deleteByUser(User user);
}
