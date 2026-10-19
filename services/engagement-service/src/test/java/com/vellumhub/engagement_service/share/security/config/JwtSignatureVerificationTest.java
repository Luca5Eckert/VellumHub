package com.vellumhub.engagement_service.share.security.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;
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
        JwtDecoder decoder = decoder(publicPem(trusted));

        String valid = sign(trusted, JWSAlgorithm.RS256);
        String forged = sign(untrusted, JWSAlgorithm.RS256);
        String legacy = sign(trusted, JWSAlgorithm.HS256);
        
        assertThat(decoder.decode(valid).getSubject()).isEqualTo("user@example.com");
        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(legacy)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsMalformedPublicKeyAtStartup() {
        Resource invalid = new ByteArrayResource("not a public key".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> decoder(invalid)).isInstanceOf(RuntimeException.class);
    }

    private JwtDecoder decoder(Resource resource) throws Exception {
        var config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "publicKeyResource", resource);
        return config.jwtDecoder();
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
