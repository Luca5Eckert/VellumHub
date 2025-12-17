package com.mrs.user_service.security.token;

import com.mrs.user_service.model.RoleUser;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.HttpServletRequest; // Ou javax, dependendo da versão do Spring

public interface TokenProvider {

    String createToken(String email, RoleUser roleUser);

    boolean validateToken(String token);

    String getUsername(String token);

    Authentication getAuthentication(String token);

    String resolveToken(HttpServletRequest request);
}