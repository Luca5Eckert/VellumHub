package com.vellumhub.auth.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Fail closed: stable, externally provisioned RSA private/public PEMs are mandatory. */
@Configuration(proxyBeanMethods = false)
public class SigningKeyConfig {
    @Bean
    SigningKeys signingKeys(
            @Value("${vellum.auth.keys.private-key-path:}") String privateKeyPath,
            @Value("${vellum.auth.keys.public-key-path:}") String publicKeyPath,
            @Value("${vellum.auth.keys.kid:}") String kid,
            @Value("${vellum.auth.keys.overlap-public-key-path:}") String overlapPath,
            @Value("${vellum.auth.keys.overlap-kid:}") String overlapKid) {
        if (privateKeyPath.isBlank() || publicKeyPath.isBlank() || kid.isBlank()) {
            throw new IllegalStateException("PoC signing keys and kid must be configured");
        }
        if (overlapPath.isBlank() != overlapKid.isBlank() || kid.equals(overlapKid)) {
            throw new IllegalStateException("Overlap requires a public key path and a distinct kid");
        }
        try {
            var rsaPrivate = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(decodePem(Path.of(privateKeyPath), "PRIVATE KEY")));
            var rsaPublic = loadPublicKey(publicKeyPath);
            if (!rsaPublic.getModulus().equals(rsaPrivate.getModulus())) {
                throw new IllegalArgumentException("RSA keys must match");
            }
            RSAKey key = new RSAKey.Builder(rsaPublic)
                    .privateKey(rsaPrivate)
                    .keyID(kid)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
            if (overlapPath.isBlank()) {
                return new SigningKeys(key, new JWKSet(key.toPublicJWK()));
            }
            var overlap = loadPublicKey(overlapPath);
            if (overlap.getModulus().equals(rsaPublic.getModulus())) {
                throw new IllegalArgumentException("Overlap key must differ from the active signing key");
            }
            var verificationKey = new RSAKey.Builder(overlap).keyID(overlapKid)
                    .algorithm(JWSAlgorithm.RS256).build();
            return new SigningKeys(key, new JWKSet(List.of(key.toPublicJWK(), verificationKey)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load trusted signing key pair", exception);
        }
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(SigningKeys keys) {
        return new ImmutableJWKSet<>(keys.publicKeys());
    }

    @Bean
    JwtEncoder jwtEncoder(SigningKeys keys) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(keys.active())));
    }

    record SigningKeys(RSAKey active, JWKSet publicKeys) {}

    private static RSAPublicKey loadPublicKey(String path) throws Exception {
        var publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(decodePem(Path.of(path), "PUBLIC KEY")));
        if (publicKey.getModulus().bitLength() < 2048) {
            throw new IllegalArgumentException("RSA public key must have at least 2048 bits");
        }
        return publicKey;
    }

    private static byte[] decodePem(Path path, String type) throws Exception {
        String raw = Files.readString(path, StandardCharsets.US_ASCII);
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        if (!raw.contains(begin) || !raw.contains(end)) {
            throw new IllegalArgumentException("Expected " + type + " PEM");
        }
        String encoded = raw.substring(raw.indexOf(begin) + begin.length(), raw.indexOf(end))
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(encoded);
    }
}
