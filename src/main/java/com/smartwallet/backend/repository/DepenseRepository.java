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
    List<Depense> findByUserOrderByDateDescIdDesc(@org.springframework.data.repository.query.Param("user") User user);

    @org.springframework.data.jpa.repository.Query("SELECT d FROM Depense d WHERE d.user = :user AND d.categorie = :categorie AND d.date >= :startDate AND d.date <= :endDate")
    List<Depense> findByUserAndCategorieAndDateBetween(
        @org.springframework.data.repository.query.Param("user") User user, 
        @org.springframework.data.repository.query.Param("categorie") com.smartwallet.backend.model.Categorie categorie, 
        @org.springframework.data.repository.query.Param("startDate") java.time.LocalDateTime startDate, 
        @org.springframework.data.repository.query.Param("endDate") java.time.LocalDateTime endDate
    );
    
    @org.springframework.data.jpa.repository.Query("SELECT d FROM Depense d WHERE d.user = :user AND d.date >= :startDate AND d.date <= :endDate")
    List<Depense> findByUserAndDateBetween(
        @org.springframework.data.repository.query.Param("user") User user, 
        @org.springframework.data.repository.query.Param("startDate") java.time.LocalDateTime startDate, 
        @org.springframework.data.repository.query.Param("endDate") java.time.LocalDateTime endDate
    );

    long countByUserAndDateAfter(
        @org.springframework.data.repository.query.Param("user") User user, 
        @org.springframework.data.repository.query.Param("date") java.time.LocalDateTime date
    );
}
