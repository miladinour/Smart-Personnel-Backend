package com.smartwallet.backend.repository;

import com.smartwallet.backend.model.Alerte;
import com.smartwallet.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlerteRepository extends JpaRepository<Alerte, Long> {
    List<Alerte> findByUserOrderByDateDesc(@org.springframework.data.repository.query.Param("user") User user);
    List<Alerte> findByUser(@org.springframework.data.repository.query.Param("user") User user);
    List<Alerte> findByUserAndConditionVerifieeFalseOrderByDateDesc(@org.springframework.data.repository.query.Param("user") User user);
}
