package com.vellumhub.recommendation_service.share.provider;

import com.vellumhub.security.jwt.JwtAccessToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org. springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class UserAuthenticationProvider {

    public UUID getUserId() {
        Jwt jwt = getJwt();
        return JwtAccessToken.userId(jwt);
    }

    public String getEmail() {
        Jwt jwt = getJwt();
        return jwt.getClaimAsString("email"); // Email is not the UUID subject; intentionally absent from new tokens.
    }

    private Jwt getJwt() {
        return (Jwt) Objects.requireNonNull(SecurityContextHolder.getContext()
                        .getAuthentication())
                .getPrincipal();
    }
}