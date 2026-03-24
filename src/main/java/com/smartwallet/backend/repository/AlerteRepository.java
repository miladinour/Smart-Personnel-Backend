package com.smartwallet.backend.repository;

import com.smartwallet.backend.model.Alerte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlerteRepository extends JpaRepository<Alerte, Long> {
}
