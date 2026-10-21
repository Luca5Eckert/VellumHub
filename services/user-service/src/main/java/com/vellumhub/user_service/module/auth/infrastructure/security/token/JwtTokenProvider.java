package com.vellumhub.user_service.module.auth.infrastructure.security.token;

import com.vellumhub.user_service.module.auth.domain.port.TokenProvider;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
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
public final class JwtTokenProvider implements TokenProvider {
    private static final long MIN_EXPIRATION_MS = 60_000L;
    private final RSAPrivateKey signingKey;
    private final String kid;
    private final long jwtExpirationMs;
    private final String issuer;
    private final String audience;
    private final String clientId;

    @Autowired
    public JwtTokenProvider(JwtKeyRing keyRing,
            @Value("${jwt.expiration-ms}") long jwtExpirationMs,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.audience}") String audience,
            @Value("${jwt.client-id}") String clientId) {
        this(keyRing.privateKey(), keyRing.activeKid(), jwtExpirationMs, issuer, audience, clientId);
    }

    /** Compatibility constructor for existing isolated signer tests, not used for production beans. */
    public JwtTokenProvider(Resource privateKeyResource, long jwtExpirationMs) throws IOException {
        this(readPrivateKey(privateKeyResource), "local-test-key", jwtExpirationMs,
                "https://auth.vellumhub.local", "api://vellumhub", "vellumhub-web");
    }

    private JwtTokenProvider(RSAPrivateKey signingKey, String kid, long duration,
                             String issuer, String audience, String clientId) {
        if (duration < MIN_EXPIRATION_MS) {
            throw new IllegalArgumentException("jwt.expiration-ms must be at least 60000 ms (1 minute)");
        }
        if (signingKey.getModulus().bitLength() < 2048) {
            throw new IllegalArgumentException("JWT signing RSA key must have at least 2048 bits");
        }
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()
                || clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("JWT issuer, audience and client-id must be configured");
        }
        this.signingKey = signingKey;
        this.kid = kid;
        this.jwtExpirationMs = duration;
        this.issuer = issuer;
        this.audience = audience;
        this.clientId = clientId;
    }

    @Override
    public String createToken(String email, UUID userId, List<String> roles) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(roles, "roles");
        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + jwtExpirationMs);
        return Jwts.builder()
                .header().keyId(kid).type("at+jwt").and()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim("client_id", clientId)
                .claim("user_id", userId.toString()) // bounded compatibility; consumers use sub
                .claim("roles", roles)
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(signingKey, Jwts.SIG.RS256)
                .compact();
    }

    private static RSAPrivateKey readPrivateKey(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return Objects.requireNonNull(RsaKeyConverters.pkcs8().convert(in),
                    "Invalid PKCS#8 RSA private key");
        }
    }
}
