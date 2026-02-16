package com.mrs.user_service.share.security.token;

import com.mrs.user_service.module.auth.infrastructure.security.token.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET_KEY =
            "VGhpcy1pcy1hLXZlcnktc2VjdXJlLWJhc2U2NC1zZWNyZXQ="; // base64
    private static final long EXPIRATION_MS = 60_000; // 1 minute

    private final JwtTokenProvider tokenProvider =
            new JwtTokenProvider(SECRET_KEY, EXPIRATION_MS);

    @Test
    @DisplayName("Should create a valid JWT with email, userId and roles")
    void shouldCreateValidJwtToken() {
        String email = "user@test.com";
        UUID userId = UUID.randomUUID();

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        );

        String token = tokenProvider.createToken(email, userId, authorities.stream().map(SimpleGrantedAuthority::getAuthority).toList());

        assertThat(token).isNotBlank();

        Claims claims = parseToken(token);

        assertThat(claims.getSubject()).isEqualTo(email);
        assertThat(claims.get("userId", String.class))
                .isEqualTo(userId.toString());
        assertThat(claims.get("roles", List.class))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        assertThat(claims.getExpiration()).isAfter(new Date());
        assertThat(claims.getIssuedAt()).isBeforeOrEqualTo(new Date());
    }

    private Claims parseToken(String token) {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        Key key = Keys.hmacShaKeyFor(keyBytes);

        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
