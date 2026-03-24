package com.smartwallet.backend.repository;

import com.smartwallet.backend.model.Objectif;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import com.smartwallet.backend.model.User;

@Repository
public interface ObjectifRepository extends JpaRepository<Objectif, Long> {
    List<Objectif> findByUser(User user);
}
