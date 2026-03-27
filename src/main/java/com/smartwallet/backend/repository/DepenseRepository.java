package com.smartwallet.backend.repository;

import com.smartwallet.backend.model.Depense;
import com.smartwallet.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DepenseRepository extends JpaRepository<Depense, Long> {
    List<Depense> findByUser(User user);
    List<Depense> findByUserAndDateBetween(User user, java.time.LocalDateTime start, java.time.LocalDateTime end);
}
