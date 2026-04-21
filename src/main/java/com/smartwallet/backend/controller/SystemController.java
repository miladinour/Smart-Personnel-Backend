package com.smartwallet.backend.controller;

import com.smartwallet.backend.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemSettingRepository systemSettingRepository;

    @GetMapping("/status")
    public ResponseEntity<?> getSystemStatus() {
        boolean maintenance = systemSettingRepository.findBySettingKey("MAINTENANCE_MODE")
                .map(s -> "true".equalsIgnoreCase(s.getSettingValue()))
                .orElse(false);
        
        return ResponseEntity.ok(Map.of(
            "maintenance", maintenance,
            "status", maintenance ? "MAINTENANCE" : "ONLINE"
        ));
    }
}
