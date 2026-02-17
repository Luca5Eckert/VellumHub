package com.mrs.catalog_service.share.service;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthenticationService {

    public UUID getAuthenticatedUserId() {
        Object principal = SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        if (principal instanceof Jwt jwt) {
            String userId = jwt.getClaimAsString("user_id");
            return UUID.fromString(userId);
        }

        throw new RuntimeException("User not authenticated or invalid token");
    }

}
