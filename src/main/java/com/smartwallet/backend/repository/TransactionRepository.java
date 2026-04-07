package com.smartwallet.backend.repository;

import com.smartwallet.backend.model.Transaction;
import com.smartwallet.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    @Transactional
    void deleteByUser(User user);

    java.util.List<Transaction> findByUserOrderByDateDesc(User user);
}
