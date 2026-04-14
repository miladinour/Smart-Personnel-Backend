package com.smartwallet.backend.repository;

import com.smartwallet.backend.model.PasswordResetToken;
import com.smartwallet.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(@org.springframework.data.repository.query.Param("token") String token);

    Optional<PasswordResetToken> findByUser(@org.springframework.data.repository.query.Param("user") User user);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteByUser(@org.springframework.data.repository.query.Param("user") User user);
}
