package com.vellumhub.security.jwt;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Strict access-token profile shared by Gateway and each autonomous resource server. */
public final class JwtAccessToken {
    private JwtAccessToken() { }

    public static OAuth2TokenValidator<Jwt> validator(String issuer, String audience, long skewSeconds) {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()
                || skewSeconds < 0 || skewSeconds > 120) {
            throw new IllegalArgumentException("Invalid JWT issuer, audience or clock skew");
        }
        OAuth2TokenValidator<Jwt> profile = jwt -> {
            if (!"RS256".equals(jwt.getHeaders().get("alg"))
                    || !"at+jwt".equals(jwt.getHeaders().get("typ"))) {
                return invalid("Unexpected access-token algorithm or type");
            }
            if (jwt.getAudience() == null || !jwt.getAudience().contains(audience)) {
                return invalid("Invalid access-token audience");
            }
            if (jwt.getSubject() == null || !isCanonicalUuid(jwt.getSubject())) {
                return invalid("Invalid access-token subject");
            }
            if (jwt.getIssuedAt() == null || jwt.getExpiresAt() == null || jwt.getId() == null
                    || jwt.getId().isBlank() || jwt.getClaimAsString("client_id") == null
                    || jwt.getClaimAsString("client_id").isBlank()) {
                return invalid("Missing required access-token claims");
            }
            if (jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(skewSeconds))
                    || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())) {
                return invalid("Invalid access-token lifetime");
            }
            String legacy = jwt.getClaimAsString("user_id");
            if (legacy != null && !legacy.equals(jwt.getSubject())) {
                return invalid("Legacy user_id disagrees with subject");
            }
            return OAuth2TokenValidatorResult.success();
        };
        return new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(issuer),
                new JwtTimestampValidator(Duration.ofSeconds(skewSeconds)),
                profile);
    }

    public static UUID userId(Jwt jwt) {
        if (jwt == null || !isCanonicalUuid(jwt.getSubject())) {
            throw new IllegalArgumentException("Expected an authenticated user UUID subject");
        }
        return UUID.fromString(jwt.getSubject());
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null) return false;
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static OAuth2TokenValidatorResult invalid(String message) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", message, null));
    }

    public static JwtAuthenticationConverter servletConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> merged = new ArrayList<>(roles.convert(jwt));
            merged.addAll(scopes.convert(jwt));
            return List.copyOf(merged);
        });
        return converter;
    }
}
