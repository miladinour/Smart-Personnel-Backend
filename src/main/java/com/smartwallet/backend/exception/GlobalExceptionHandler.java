package com.smartwallet.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(org.springframework.security.authentication.BadCredentialsException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Email ou mot de passe incorrect");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        System.err.println(">>> GlobalExceptionHandler - RuntimeException: " + ex.getMessage());
        Map<String, String> response = new HashMap<>();
        
        // On considère souvent les RuntimeException lancées par nous comme des 400 (Bad Request)
        // si elles contiennent un message métier.
        String message = ex.getMessage();
        HttpStatus status = HttpStatus.BAD_REQUEST;

        if (message == null || message.isEmpty()) {
            message = "Une erreur inattendue est survenue";
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        System.err.println(">>> GlobalExceptionHandler - General Exception: " + ex.getMessage());
        ex.printStackTrace();
        Map<String, String> response = new HashMap<>();
        
        String message = ex.getMessage();
        if (message == null || message.contains("Internal Server Error")) {
            message = "Une erreur interne s'est produite sur le serveur";
        }
        
        response.put("message", message);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
