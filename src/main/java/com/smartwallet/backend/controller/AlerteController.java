package com.smartwallet.backend.controller;

import com.smartwallet.backend.dto.AlerteDTO;
import com.smartwallet.backend.model.Alerte;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.repository.AlerteRepository;
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
@CrossOrigin("*")
public class AlerteController {

    private final AlerteRepository alerteRepository;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<AlerteDTO>> getUserAlerts(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        List<Alerte> alertes = alerteRepository.findByUserOrderByDateDesc(user);
        
        List<AlerteDTO> dtos = alertes.stream().map(alerte -> {
            AlerteDTO dto = new AlerteDTO();
            dto.setId(alerte.getId());
            dto.setDate(alerte.getDate());
            dto.setMessage(alerte.getMessage());
            dto.setConditionVerifiee(alerte.isConditionVerifiee());
            
            if (alerte.getBudget() != null) {
                dto.setBudgetId(alerte.getBudget().getId());
                if (alerte.getBudget().getCategorie() != null) {
                    dto.setCategorieNom(alerte.getBudget().getCategorie().getNom());
                }
            }
            return dto;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }
    
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable("id") Long id, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        Alerte alerte = alerteRepository.findById(id).orElse(null);
        
        if (alerte != null && alerte.getUser().getId().equals(user.getId())) {
            alerte.setConditionVerifiee(true);
            alerteRepository.save(alerte);
        }
        return ResponseEntity.ok().build();
    }
}
