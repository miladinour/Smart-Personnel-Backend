package com.smartwallet.backend.controller;

import com.smartwallet.backend.model.AuditLog;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.service.UserService;
import com.smartwallet.backend.repository.AdminRepository;
import com.smartwallet.backend.repository.AuditLogRepository;
import com.smartwallet.backend.repository.TransactionRepository;
import com.smartwallet.backend.repository.SystemSettingRepository;
import com.smartwallet.backend.model.SystemSetting;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final AdminRepository adminRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogRepository auditLogRepository;
    private final SystemSettingRepository systemSettingRepository;

    private boolean isAdmin(Authentication authentication) {
        return adminRepository.findByEmail(authentication.getName()).isPresent();
    }

    // ─── GET ALL USERS ───────────────────────────────────────────────────────────
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body("Accès refusé : Réservé aux administrateurs.");
        }
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // ─── TOGGLE USER STATUS (with audit logging) ─────────────────────────────────
    @PutMapping("/users/{id}/status")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable Long id,
            @RequestParam boolean enabled,
            Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body("Accès refusé.");
        }

        // Find the target user for the audit log
        List<User> allUsers = userService.getAllUsers();
        Optional<User> target = allUsers.stream().filter(u -> u.getId().equals(id)).findFirst();
        String targetEmail = target.map(u -> u.getEmail() != null ? u.getEmail() : "Utilisateur #" + id).orElse("Utilisateur #" + id);

        // Perform the update
        userService.updateUserStatus(id, enabled);

        // Record a real audit trace ONLY if audit tracing is enabled
        boolean auditEnabled = systemSettingRepository.findBySettingKey("AUDIT_TRACING")
                .map(s -> "true".equalsIgnoreCase(s.getSettingValue()))
                .orElse(true); // Default to true if not set yet

        if (auditEnabled) {
            AuditLog log = new AuditLog(
                authentication.getName(),
                enabled ? "Activation du compte utilisateur" : "Désactivation du compte utilisateur",
                targetEmail,
                enabled ? "SUCCES" : "AVERTISSEMENT"
            );
            auditLogRepository.save(log);
        }

        return ResponseEntity.ok(Map.of("message", "Statut mis à jour avec succès."));
    }

    // ─── GET GLOBAL STATS ────────────────────────────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<?> getGlobalStats(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body("Accès refusé.");
        }

        List<User> users = userService.getAllUsers();
        long transactionCount = transactionRepository.count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", users.size());
        stats.put("activeUsers", users.stream().filter(User::isEnabled).count());
        stats.put("totalTransactions", transactionCount);

        // Distribution par Pays
        Map<String, Long> countryStats = users.stream()
            .filter(u -> u.getPays() != null && !u.getPays().isEmpty())
            .collect(Collectors.groupingBy(User::getPays, Collectors.counting()));
        stats.put("countryStats", countryStats);

        // Distribution par Genre
        Map<String, Long> genderStats = users.stream()
            .filter(u -> u.getGenre() != null)
            .collect(Collectors.groupingBy(User::getGenre, Collectors.counting()));
        stats.put("genderStats", genderStats);

        // Tendance des Inscriptions
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");
        Map<String, Long> registrationTrend = users.stream()
            .filter(u -> u.getDateCreation() != null)
            .collect(Collectors.groupingBy(
                u -> u.getDateCreation().format(formatter),
                TreeMap::new,
                Collectors.counting()
            ));

        // Tendance des Transactions
        Map<String, Long> transactionTrend = transactionRepository.findAll().stream()
            .filter(t -> t.getDate() != null)
            .collect(Collectors.groupingBy(
                t -> t.getDate().format(formatter),
                TreeMap::new,
                Collectors.counting()
            ));

        stats.put("registrationTrend", registrationTrend);
        stats.put("transactionTrend", transactionTrend);

        return ResponseEntity.ok(stats);
    }

    // ─── GET AUDIT LOGS ──────────────────────────────────────────────────────────
    @GetMapping("/audit")
    public ResponseEntity<?> getAuditLogs(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body("Accès refusé.");
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        List<Map<String, Object>> result = auditLogRepository.findAllByOrderByDateDesc()
            .stream()
            .map(log -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", log.getId());
                entry.put("admin", log.getAdmin());
                entry.put("action", log.getAction());
                entry.put("target", log.getTarget());
                entry.put("status", log.getStatus());
                entry.put("date", log.getDate().format(fmt));
                return entry;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ─── SYSTEM SETTINGS ───────────────────────────────────────────────────────
    @GetMapping("/settings")
    public ResponseEntity<?> getSettings(Authentication authentication) {
        if (!isAdmin(authentication)) return ResponseEntity.status(403).build();
        
        List<SystemSetting> settings = systemSettingRepository.findAll();
        Map<String, String> resultMap = settings.stream()
                .collect(Collectors.toMap(SystemSetting::getSettingKey, SystemSetting::getSettingValue));
        
        // Ensure defaults exist in response if not in DB
        resultMap.putIfAbsent("MAINTENANCE_MODE", "false");
        resultMap.putIfAbsent("AUDIT_TRACING", "true");
        
        return ResponseEntity.ok(resultMap);
    }

    @PutMapping("/settings/{key}")
    public ResponseEntity<?> updateSetting(
            @PathVariable String key,
            @RequestParam String value,
            Authentication authentication) {
        if (!isAdmin(authentication)) return ResponseEntity.status(403).build();

        SystemSetting setting = systemSettingRepository.findBySettingKey(key)
                .orElse(new SystemSetting(key, value));
        
        setting.setSettingValue(value);
        systemSettingRepository.save(setting);

        // Add to audit log that a setting was changed (this is a critical action, always log if possible or follow flag)
        boolean auditEnabled = systemSettingRepository.findBySettingKey("AUDIT_TRACING")
                .map(s -> "true".equalsIgnoreCase(s.getSettingValue()))
                .orElse(true);

        if (auditEnabled) {
            AuditLog log = new AuditLog(
                authentication.getName(),
                "Modification paramètre système : " + key,
                "Valeur: " + value,
                "INFO"
            );
            auditLogRepository.save(log);
        }

        return ResponseEntity.ok(Map.of("message", "Paramètre mis à jour"));
    }
}
