package com.vellumhub.user_service.module.auth.infrastructure.security.token;

import com.vellumhub.user_service.module.auth.domain.port.TokenProvider;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class JwtTokenProvider implements TokenProvider {

    private static final long MIN_EXPIRATION_MS = 60_000;
    private static final int MIN_RSA_BITS = 2048;

    private final RSAPrivateKey signingKey;
    private final long jwtExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.private-key-location}") Resource privateKeyResource,
            @Value("${jwt.expiration-ms}") long jwtExpirationMs) throws IOException {
        if (jwtExpirationMs < MIN_EXPIRATION_MS) {
            throw new IllegalArgumentException("jwt.expiration-ms must be at least 60000 ms (1 minute)");
        }
        this.jwtExpirationMs = jwtExpirationMs;
        try (InputStream inputStream = privateKeyResource.getInputStream()) {
            signingKey = Objects.requireNonNull(RsaKeyConverters.pkcs8().convert(inputStream),
                    "Invalid PKCS#8 RSA private key");
        }
        if (signingKey.getModulus().bitLength() < MIN_RSA_BITS) {
            throw new IllegalArgumentException("JWT signing RSA key must have at least 2048 bits");
        }
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
                .signWith(signingKey, Jwts.SIG.RS256)
                .compact();
    }
}
