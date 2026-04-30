package com.vellumhub.user_service.share.security.token;

import com.vellumhub.user_service.module.auth.infrastructure.security.token.JwtTokenProvider;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(claims.get("user_id", String.class))
                .isEqualTo(userId.toString());
        assertThat(claims.get("roles", List.class))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        assertThat(claims.getExpiration()).isAfter(new Date());
        assertThat(claims.getIssuedAt()).isBeforeOrEqualTo(new Date());
    }

    @Test
    @DisplayName("Should reject JWT expiration below one minute")
    void shouldRejectJwtExpirationBelowOneMinute() {
        assertThatThrownBy(() -> new JwtTokenProvider(SECRET_KEY, 59_999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt.expiration-ms")
                .hasMessageContaining("60000");
    }

    @Test
    @DisplayName("Should reject JWT secret that is not Base64 encoded")
    void shouldRejectJwtSecretThatIsNotBase64Encoded() {
        assertThatThrownBy(() -> new JwtTokenProvider("not-a-base64-secret!", EXPIRATION_MS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt.secret")
                .hasMessageContaining("Base64");
    }

    @Test
    @DisplayName("Should reject JWT secret shorter than 32 decoded bytes")
    void shouldRejectJwtSecretShorterThanThirtyTwoDecodedBytes() {
        assertThatThrownBy(() -> new JwtTokenProvider("dG9vLXNob3J0", EXPIRATION_MS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt.secret")
                .hasMessageContaining("32 bytes");
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
