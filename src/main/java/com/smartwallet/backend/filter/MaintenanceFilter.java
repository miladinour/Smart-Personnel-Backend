package com.smartwallet.backend.filter;

import com.smartwallet.backend.repository.AdminRepository;
import com.smartwallet.backend.repository.SystemSettingRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class MaintenanceFilter extends OncePerRequestFilter {

    private final SystemSettingRepository systemSettingRepository;
    private final AdminRepository adminRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Whitelist public and admin paths
        if (path.startsWith("/api/auth/") || 
            path.startsWith("/api/admin/") || 
            path.startsWith("/api/system/") || 
            path.startsWith("/uploads/") ||
            path.equals("/error")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check if maintenance mode is ON
        boolean maintenanceMode = systemSettingRepository.findBySettingKey("MAINTENANCE_MODE")
                .map(s -> "true".equalsIgnoreCase(s.getSettingValue()))
                .orElse(false);

        if (maintenanceMode) {
            // Check if current user is admin
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal().toString())) {
                boolean isAdmin = adminRepository.findByEmail(auth.getName()).isPresent();
                if (isAdmin) {
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            // Block standard users
            response.setStatus(503);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\": \"MAINTENANCE\", \"message\": \"Le système est actuellement en maintenance. Veuillez revenir plus tard.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
