package com.smartwallet.backend.repository;

import com.smartwallet.backend.model.Objectif;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import com.smartwallet.backend.model.User;

@Repository
public interface ObjectifRepository extends JpaRepository<Objectif, Long> {
    List<Objectif> findByUser(@org.springframework.data.repository.query.Param("user") User user);

    @Query("SELECT o FROM Objectif o WHERE o.atteint = false AND o.dateLimite IS NOT NULL AND o.dateLimite <= :today")
    List<Objectif> findExpiredUnachieved(@Param("today") LocalDate today);

    @org.springframework.transaction.annotation.Transactional
    void deleteByUser(User user);
}
