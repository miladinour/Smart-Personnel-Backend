package com.smartwallet.backend.repository;

import com.smartwallet.backend.model.Revenu;
import com.smartwallet.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RevenuRepository extends JpaRepository<Revenu, Long> {
    List<Revenu> findByUser(@org.springframework.data.repository.query.Param("user") User user);

    @org.springframework.data.jpa.repository.Query("SELECT r FROM Revenu r WHERE r.user = :user AND r.date >= :startDate AND r.date <= :endDate")
    List<Revenu> findByUserAndDateBetween(
        @org.springframework.data.repository.query.Param("user") User user, 
        @org.springframework.data.repository.query.Param("startDate") java.time.LocalDateTime startDate, 
        @org.springframework.data.repository.query.Param("endDate") java.time.LocalDateTime endDate
    );
}
