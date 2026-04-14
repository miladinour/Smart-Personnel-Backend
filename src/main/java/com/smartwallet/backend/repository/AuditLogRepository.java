package com.smartwallet.backend.repository;

import com.smartwallet.backend.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    // Récupérer tous les logs triés du plus récent au plus ancien
    List<AuditLog> findAllByOrderByDateDesc();
}
