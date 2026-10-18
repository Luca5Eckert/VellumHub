package com.vellumhub.user_service.share.security.token;

import com.vellumhub.user_service.module.auth.infrastructure.security.token.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {
    private static final long EXPIRATION_MS = 60_000;

    private JwtTokenProvider provider() throws Exception {
        return new JwtTokenProvider(TestRsaKeyMaterial.privateResource(), EXPIRATION_MS);
    }

    private NimbusJwtDecoder decoder() {
        return NimbusJwtDecoder.withPublicKey(TestRsaKeyMaterial.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
    }

    @Test
    void issuesAnRs256JwtValidatedWithoutAccessToThePrivateKey() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = provider().createToken("user@test.com", userId, List.of("ROLE_USER", "ROLE_ADMIN"));
        Jwt jwt = decoder().decode(token);

        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
        assertThat(jwt.getSubject()).isEqualTo("user@test.com");
        assertThat(jwt.getClaimAsString("user_id")).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ROLE_USER", "ROLE_ADMIN");
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
    }

    @Test
    void rejectsAlteredPayload() throws Exception {
        String token = provider().createToken("user@test.com", UUID.randomUUID(), List.of("ROLE_USER"));
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String alteredPayload = payload.replace("user@test.com", "admin@test.com");
        String alteredToken = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(alteredPayload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertThat(alteredPayload).isNotEqualTo(payload);
        assertThatThrownBy(() -> decoder().decode(alteredToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongRsaSignature() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var other = generator.generateKeyPair();
        var encoded = java.util.Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(other.getPrivate().getEncoded());
        var pem = "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
        String forged = new JwtTokenProvider(
                new ByteArrayResource(pem.getBytes(StandardCharsets.US_ASCII)), EXPIRATION_MS)
                .createToken("user@test.com", UUID.randomUUID(), List.of("ROLE_USER"));

        assertThatThrownBy(() -> decoder().decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsLegacyHs256AfterCutover() {
        String legacy = Jwts.builder().subject("user@test.com")
                .signWith(Keys.hmacShaKeyFor(new byte[32]), Jwts.SIG.HS256).compact();
        assertThatThrownBy(() -> decoder().decode(legacy)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsInvalidExpiration() {
        assertThatThrownBy(() -> new JwtTokenProvider(TestRsaKeyMaterial.privateResource(), 59_999))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("jwt.expiration-ms");
    }

    @Test
    void rejectsWeakRsaPrivateKeys() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        var weak = generator.generateKeyPair();
        String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(weak.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";

        assertThatThrownBy(() -> new JwtTokenProvider(
                new ByteArrayResource(pem.getBytes(StandardCharsets.US_ASCII)), EXPIRATION_MS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2048");
    }

    @Test
    void rejectsInvalidPrivateKeyMaterial() {
        assertThatThrownBy(() -> new JwtTokenProvider(
                new ByteArrayResource("not a PKCS8 PEM".getBytes(StandardCharsets.US_ASCII)),
                EXPIRATION_MS)).isInstanceOf(RuntimeException.class);
    }
}
