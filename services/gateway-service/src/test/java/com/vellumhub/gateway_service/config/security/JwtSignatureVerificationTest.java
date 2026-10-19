package com.vellumhub.gateway_service.config.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Checks the actual Spring decoder: no private key or shared HMAC secret is required. */
class JwtSignatureVerificationTest {

    @Test
    void acceptsOnlyRsaTokensSignedByTrustedPrivateKey() throws Exception {
        KeyPair trusted = keyPair();
        KeyPair untrusted = keyPair();
        ReactiveJwtDecoder decoder = decoder(publicPem(trusted));

        String valid = sign(trusted, JWSAlgorithm.RS256);
        String forged = sign(untrusted, JWSAlgorithm.RS256);
        String legacy = sign(trusted, JWSAlgorithm.HS256);
        
        assertThat(decoder.decode(valid).block().getSubject()).isEqualTo("user@example.com");
        assertThatThrownBy(() -> decoder.decode(forged).block()).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(legacy).block()).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode("not-a-jwt").block()).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredRsaTokens() throws Exception {
        KeyPair trusted = keyPair();
        var token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder()
                .subject("user@example.com")
                .issueTime(Date.from(Instant.now().minusSeconds(3600)))
                .expirationTime(Date.from(Instant.now().minusSeconds(300)))
                .build());
        token.sign(new RSASSASigner((RSAPrivateKey) trusted.getPrivate()));

        ReactiveJwtDecoder decoder = decoder(publicPem(trusted));
        assertThatThrownBy(() -> decoder.decode(token.serialize()).block())
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWeakRsaPublicKeys() throws Exception {
        KeyPairGenerator weakGenerator = KeyPairGenerator.getInstance("RSA");
        weakGenerator.initialize(1024);

        assertThatThrownBy(() -> decoder(publicPem(weakGenerator.generateKeyPair())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2048");
    }

    @Test
    void rejectsMalformedPublicKeyAtStartup() {
        Resource invalid = new ByteArrayResource("not a public key".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> decoder(invalid)).isInstanceOf(RuntimeException.class);
    }

    private ReactiveJwtDecoder decoder(Resource resource) throws Exception {
        return new GatewaySecurityConfig(resource, false, "http://localhost:3000").reactiveJwtDecoder();
    }

    private Resource publicPem(KeyPair pair) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pair.getPublic().getEncoded());
        return new ByteArrayResource(("-----BEGIN PUBLIC KEY-----\n"
                + base64 + "\n-----END PUBLIC KEY-----\n").getBytes(StandardCharsets.US_ASCII));
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String sign(KeyPair pair, JWSAlgorithm algorithm) throws Exception {
        var token = new SignedJWT(new JWSHeader(algorithm), new JWTClaimsSet.Builder()
                .subject("user@example.com")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build());
        if (algorithm.equals(JWSAlgorithm.RS256)) {
            token.sign(new RSASSASigner((RSAPrivateKey) pair.getPrivate()));
        } else {
            token.sign(new MACSigner(new byte[32]));
        }
        return token.serialize();
    }
}
