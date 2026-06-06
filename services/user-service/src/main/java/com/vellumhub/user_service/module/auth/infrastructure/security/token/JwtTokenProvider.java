package com.vellumhub.user_service.module.auth.infrastructure.security.token;

import com.vellumhub.user_service.module.auth.domain.port.TokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider implements TokenProvider {

    private static final long MIN_EXPIRATION_MS = 60_000;

    private static final int MIN_SECRET_KEY_BYTES = 32;

    private final Key key;

    private final long jwtExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.expiration-ms}") long jwtExpirationMs) {

        validateExpiration(jwtExpirationMs);
        this.jwtExpirationMs = jwtExpirationMs;
        byte[] keyBytes = decodeAndValidateSecret(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String createToken(String email, UUID userId, List<String> roles) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .setSubject(email)
                .claim("user_id", userId)
                .claim("roles", roles)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private static void validateExpiration(long jwtExpirationMs) {
        if (jwtExpirationMs < MIN_EXPIRATION_MS) {
            throw new IllegalArgumentException(
                    "jwt.expiration-ms must be at least " + MIN_EXPIRATION_MS + " ms (1 minute)");
        }
    }

    private static byte[] decodeAndValidateSecret(String secretKey) {
        byte[] keyBytes;

        try {
            keyBytes = Decoders.BASE64.decode(secretKey);
        } catch (DecodingException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("jwt.secret must be a valid Base64-encoded value", exception);
        }

        if (keyBytes.length < MIN_SECRET_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "jwt.secret must decode to at least " + MIN_SECRET_KEY_BYTES + " bytes");
        }

        return keyBytes;
    }
}
