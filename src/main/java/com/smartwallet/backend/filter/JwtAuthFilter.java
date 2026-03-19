package com.smartwallet.backend.filter;

import com.smartwallet.backend.model.User;
import com.smartwallet.backend.repository.UserRepository;
import com.smartwallet.backend.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        String token = null;
        String email = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            try {
                email = jwtService.extractEmail(token);
            } catch (Exception e) {
                System.out.println(">>> JwtAuthFilter - ERREUR : Impossible d'extraire l'email : " + e.getMessage());
            }
        }
        System.out.println(">>> JwtAuthFilter - Path: " + request.getRequestURI() + " - Token: "
                + (token != null ? "Présent" : "Absent") + " - Email: " + email);

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            User user = userRepository.findByEmail(email).orElse(null);
            if (user != null) {
                if (jwtService.isTokenValid(token)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            user.getEmail(),
                            null,
                            user.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    System.out.println(">>> JwtAuthFilter - Authentification réussie pour : " + email);
                } else {
                    System.out.println(">>> JwtAuthFilter - Token invalide pour : " + email);
                }
            } else {
                System.out.println(">>> JwtAuthFilter - Utilisateur non trouvé en base pour l'email : " + email);
            }
        }
        filterChain.doFilter(request, response);
    }
}
// Ce filtre intercepte chaque requête pour vérifier la présence et la validité
// du token