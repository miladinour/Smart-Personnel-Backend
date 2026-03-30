package com.smartwallet.backend.service;

import com.smartwallet.backend.model.Dette;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.repository.DetteRepository;
import com.smartwallet.backend.model.Revenu;
import com.smartwallet.backend.model.Depense;
import com.smartwallet.backend.model.DetteType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DetteService {

    @Autowired
    private DetteRepository detteRepository;

    @Autowired
    private RevenuService revenuService;

    @Autowired
    private DepenseService depenseService;

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
            if (!dette.getIsPaye()) {
                Double remaining = dette.getMontant() - dette.getMontantPaye();
                this.addPayment(id, remaining);
            }
            return detteRepository.findById(id).orElse(dette);
        }
        return null;
    }

    public Dette addPayment(Long id, Double amount) {
        Dette dette = detteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dette introuvable"));

        if (dette.getIsPaye()) {
            throw new RuntimeException("Dette déjà réglée en totalité");
        }

        dette.setMontantPaye(dette.getMontantPaye() + amount);

        if (dette.getMontantPaye() >= dette.getMontant()) {
            dette.setIsPaye(true);
            dette.setMontantPaye(dette.getMontant()); // cap to max
        }

        // Create transaction Based on Debt Type
        if (dette.getType() == DetteType.PRETE) {
            // They pay me -> Revenu
            Revenu revenu = new Revenu();
            revenu.setMontant(java.math.BigDecimal.valueOf(amount));
            revenu.setDescription(dette.getAmi() + " vous a payé " + amount);
            revenu.setDate(java.time.LocalDateTime.now());
            revenuService.createRevenu(revenu, dette.getUser());
        } else {
            // I pay them -> Depense
            Depense depense = new Depense();
            depense.setMontant(java.math.BigDecimal.valueOf(amount));
            depense.setDescription("vous avez payé " + amount + " à " + dette.getAmi());
            depense.setDate(java.time.LocalDateTime.now());
            depenseService.createDepense(depense, dette.getUser());
        }

        return detteRepository.save(dette);
    }

    public void deleteAllByUser(User user) {
        detteRepository.deleteByUser(user);
    }
}
