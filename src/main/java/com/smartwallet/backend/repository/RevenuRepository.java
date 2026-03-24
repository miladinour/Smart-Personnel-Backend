package com.smartwallet.backend.repository;

import com.smartwallet.backend.model.Revenu;
import com.smartwallet.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RevenuRepository extends JpaRepository<Revenu, Long> {
    List<Revenu> findByUser(User user);
}
