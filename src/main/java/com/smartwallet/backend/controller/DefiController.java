package com.smartwallet.backend.controller;

import com.smartwallet.backend.model.Defi;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.service.DefiService;
import com.smartwallet.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/defis")
@RequiredArgsConstructor
@CrossOrigin("*")
public class DefiController {

    private final DefiService defiService;
    private final UserService userService;

    @GetMapping("/active")
    public ResponseEntity<List<Defi>> getActiveDefis(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(defiService.getActiveDefis(user));
    }

    @PostMapping("/accept")
    public ResponseEntity<Defi> acceptDefi(@RequestBody Map<String, Object> payload, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        String titre = (String) payload.get("titre");
        String description = (String) payload.get("description");
        String category = (String) payload.get("category");
        
        int duration = 7;
        if (payload.get("duration") != null) {
            try {
                duration = Integer.parseInt(payload.get("duration").toString());
            } catch (Exception e) {
                duration = 7;
            }
        }
        
        return ResponseEntity.ok(defiService.acceptDefi(user, titre, description, category, duration));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDefi(@PathVariable Long id, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        defiService.deleteDefi(id, user);
        return ResponseEntity.noContent().build();
    }
}
