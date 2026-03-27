package com.smartwallet.backend.service;

import com.smartwallet.backend.model.Dette;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.repository.DetteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DetteService {

    @Autowired
    private DetteRepository detteRepository;

    public List<Dette> getDettesByUser(User user) {
        return detteRepository.findByUserOrderByDateDesc(user);
    }

    public List<Dette> getActiveDettes(User user) {
        return detteRepository.findByUserAndIsPayeOrderByDateDesc(user, false);
    }

    public List<Dette> getSettledDettes(User user) {
        return detteRepository.findByUserAndIsPayeOrderByDateDesc(user, true);
    }

    public Dette saveDette(Dette dette) {
        return detteRepository.save(dette);
    }

    public Optional<Dette> getDetteById(Long id) {
        return detteRepository.findById(id);
    }

    public void deleteDette(Long id) {
        detteRepository.deleteById(id);
    }

    public Dette markAsPaye(Long id) {
        Optional<Dette> optional = detteRepository.findById(id);
        if (optional.isPresent()) {
            Dette dette = optional.get();
            dette.setIsPaye(true);
            return detteRepository.save(dette);
        }
        return null;
    }
}
