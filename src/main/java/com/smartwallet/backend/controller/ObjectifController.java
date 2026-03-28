package com.smartwallet.backend.controller;

import com.smartwallet.backend.model.Objectif;
import com.smartwallet.backend.model.User;
import com.smartwallet.backend.service.ObjectifService;
import com.smartwallet.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/objectifs")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ObjectifController {

    private final ObjectifService objectifService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<Objectif>> getAllObjectifs(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(objectifService.getObjectifsByUser(user));
    }

    @PostMapping
    public ResponseEntity<Objectif> createObjectif(@RequestBody Objectif objectif, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(objectifService.createObjectif(objectif, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Objectif> updateObjectif(@PathVariable("id") Long id, @RequestBody Objectif objectif,
            Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(objectifService.updateObjectif(id, objectif, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteObjectif(@PathVariable("id") Long id, Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        objectifService.deleteObjectif(id, user);
        return ResponseEntity.noContent().build();
    }
}
