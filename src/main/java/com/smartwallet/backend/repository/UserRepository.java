package com.smartwallet.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartwallet.backend.model.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(@org.springframework.data.repository.query.Param("email") String email);
}