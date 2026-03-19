package com.smartwallet.backend.controller;

import com.smartwallet.backend.repository.UserRepository;
import com.smartwallet.backend.repository.DepenseRepository;
import com.smartwallet.backend.repository.RevenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugController {

    private final UserRepository userRepository;
    private final DepenseRepository depenseRepository;
    private final RevenuRepository revenuRepository;

    @GetMapping("/inspect")
    public Map<String, Object> inspect() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalDepenses", depenseRepository.count());
        stats.put("totalRevenus", revenuRepository.count());
        stats.put("users", userRepository.findAll().stream().map(u -> {
            Map<String, String> um = new HashMap<>();
            um.put("email", u.getEmail());
            um.put("id", String.valueOf(u.getId()));
            return um;
        }).toList());
        return stats;
    }
}
