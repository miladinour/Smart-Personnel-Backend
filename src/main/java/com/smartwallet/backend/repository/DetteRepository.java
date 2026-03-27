package com.smartwallet.backend.repository;

import com.smartwallet.backend.model.Dette;
import com.smartwallet.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DetteRepository extends JpaRepository<Dette, Long> {
    List<Dette> findByUserOrderByDateDesc(User user);
    List<Dette> findByUserAndIsPayeOrderByDateDesc(User user, boolean isPaye);
}
