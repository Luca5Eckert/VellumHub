package com.vellumhub.auth.config;

import com.vellumhub.auth.identity.IdentityRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Revalidate AS browser sessions locally. Resource servers still validate JWTs without DB calls. */
final class ActiveIdentitySessionFilter extends OncePerRequestFilter {
    private final IdentityRepository identities;

    ActiveIdentitySessionFilter(IdentityRepository identities) { this.identities = identities; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            boolean active;
            try {
                active = identities.findById(UUID.fromString(authentication.getName()))
                        .filter(IdentityRepository.Identity::active).isPresent();
            } catch (IllegalArgumentException invalidIdentity) {
                active = false;
            }
            if (!active) {
                var session = request.getSession(false);
                if (session != null) { session.invalidate(); }
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
