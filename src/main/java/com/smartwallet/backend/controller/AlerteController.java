package com.smartwallet.backend.controller;

import com.smartwallet.backend.dto.AlerteDTO;
import com.smartwallet.backend.model.Alerte;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.repository.AlerteRepository;
import com.smartwallet.backend.dto.AlertResponse;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.service.AlerteService;
import com.smartwallet.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/alertes")
@RequiredArgsConstructor
public class AlerteController {

    private final AlerteService alerteService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<AlertResponse>> getAlertes(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(alerteService.getAlertesByUser(user));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        alerteService.markAsRead(id, user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlerte(@PathVariable Long id, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        alerteService.deleteAlerte(id, user);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/all")
    public ResponseEntity<Void> deleteAll(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        alerteService.deleteAllByUser(user);
        return ResponseEntity.noContent().build();
    }
}
