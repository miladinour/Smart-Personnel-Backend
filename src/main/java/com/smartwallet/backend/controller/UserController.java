package com.smartwallet.backend.controller;

import com.smartwallet.backend.model.User;
import com.smartwallet.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final com.smartwallet.backend.service.FirebaseService firebaseService;

    private boolean isOwner(Long id, Authentication authentication) {
        User user = userService.findById(id);
        return user.getEmail().equals(authentication.getName());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable("id") Long id, Authentication authentication) {
        if (!isOwner(id, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message",
                            "Accès refusé : Vous ne pouvez pas consulter le profil d'un autre utilisateur."));
        }
        return ResponseEntity.ok(userService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable("id") Long id, @RequestBody User userDetails,
            Authentication authentication) {
        if (!isOwner(id, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message",
                            "Accès refusé : Vous ne pouvez pas modifier le profil d'un autre utilisateur."));
        }
        return ResponseEntity.ok(userService.updateUser(id, userDetails));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable("id") Long id, Authentication authentication) {
        if (!isOwner(id, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message",
                            "Accès refusé : Vous ne pouvez pas supprimer le compte d'un autre utilisateur."));
        }
        userService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "Utilisateur supprimé avec succès."));
    }

    @PostMapping("/{id}/photo")
    public ResponseEntity<?> uploadPhoto(@PathVariable("id") Long id, @RequestBody Map<String, String> payload,
            Authentication authentication) {
        if (!isOwner(id, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Accès refusé."));
        }
        return ResponseEntity.ok(userService.updateProfilePicture(id, payload.get("photoUrl")));
    }

    @PostMapping("/{id}/upload-photo")
    public ResponseEntity<?> uploadPhotoFile(@PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        if (!isOwner(id, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Accès refusé."));
        }
        try {
            // Simple file storage logic for PFE
            String fileName = "user_" + id + "_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path path = Paths.get("uploads/" + fileName);
            Files.createDirectories(path.getParent());
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

            String photoUrl = "/uploads/" + fileName; // Assuming server serves /uploads
            return ResponseEntity.ok(userService.updateProfilePicture(id, photoUrl));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erreur lors de l'upload: " + e.getMessage()));
        }
    }

    @PatchMapping("/{id}/fcm-token")
    public ResponseEntity<?> updateFcmToken(@PathVariable("id") Long id, @RequestBody Map<String, String> payload,
            Authentication authentication) {
        if (!isOwner(id, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Accès refusé."));
        }
        userService.updateFcmToken(id, payload.get("token"));
        return ResponseEntity.ok(Map.of("message", "Token FCM mis à jour avec succès."));
    }

    @PostMapping("/{id}/test-notification")
    public ResponseEntity<?> testNotification(@PathVariable("id") Long id, Authentication authentication) {
        if (!isOwner(id, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Accès refusé."));
        }
        User user = userService.findById(id);
        firebaseService.sendPushNotification(user, "Test Push", "Ceci est une notification de test de Smart Finance !");
        return ResponseEntity.ok(Map.of("message", "Notification de test envoyée !"));
    }
}
