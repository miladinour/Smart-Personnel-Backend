package com.smartwallet.backend.repository;

import com.smartwallet.backend.model.Defi;
import com.smartwallet.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DefiRepository extends JpaRepository<Defi, Long> {
    List<Defi> findByUserAndActiveTrue(User user);
    List<Defi> findByUser(User user);
}
