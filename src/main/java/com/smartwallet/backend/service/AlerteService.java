package com.smartwallet.backend.service;

import com.smartwallet.backend.dto.AlertResponse;
import com.smartwallet.backend.model.Alerte;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.repository.AlerteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlerteService {

    private final AlerteRepository alerteRepository;

    public List<AlertResponse> getAlertesByUser(User user) {
        return alerteRepository.findByUser(user).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public void markAsRead(Long id, User user) {
        Alerte alerte = alerteRepository.findById(id)
                .filter(a -> a.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Alerte non trouvée"));
        alerte.setConditionVerifiee(true);
        alerteRepository.save(alerte);
    }

    public void deleteAlerte(Long id, User user) {
        Alerte alerte = alerteRepository.findById(id)
                .filter(a -> a.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Alerte non trouvée"));
        alerteRepository.delete(alerte);
    }

    public void deleteAllByUser(User user) {
        List<Alerte> alertes = alerteRepository.findByUser(user);
        alerteRepository.deleteAll(alertes);
    }

    private AlertResponse mapToResponse(Alerte alerte) {
        return AlertResponse.builder()
                .id(alerte.getId())
                .date(alerte.getDate())
                .message(alerte.getMessage())
                .conditionVerifiee(alerte.isConditionVerifiee())
                .budgetId(alerte.getBudget() != null ? alerte.getBudget().getId() : null)
                .categorieNom(alerte.getBudget() != null && alerte.getBudget().getCategorie() != null ? 
                        alerte.getBudget().getCategorie().getNom() : null)
                .build();
    }
}
