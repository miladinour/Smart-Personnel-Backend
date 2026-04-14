package com.smartwallet.backend.controller;

import com.smartwallet.backend.model.Transaction;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.repository.TransactionRepository;
import com.smartwallet.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionRepository transactionRepository;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<Transaction>> getAllTransactions(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        List<Transaction> transactions = transactionRepository.findByUserOrderByDateDesc(user);
        return ResponseEntity.ok(transactions);
    }
}
