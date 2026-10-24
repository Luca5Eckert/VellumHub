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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Fail closed: stable, externally provisioned RSA private/public PEMs are mandatory. */
@Configuration(proxyBeanMethods = false)
public class SigningKeyConfig {
    @Bean
    JWKSource<SecurityContext> jwkSource(
            @Value("${vellum.auth.keys.private-key-path:}") String privateKeyPath,
            @Value("${vellum.auth.keys.public-key-path:}") String publicKeyPath,
            @Value("${vellum.auth.keys.kid:}") String kid) {
        if (privateKeyPath.isBlank() || publicKeyPath.isBlank() || kid.isBlank()) {
            throw new IllegalStateException("PoC signing keys and kid must be configured");
        }
        try {
            var rsaPrivate = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(decodePem(Path.of(privateKeyPath), "PRIVATE KEY")));
            var rsaPublic = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(decodePem(Path.of(publicKeyPath), "PUBLIC KEY")));
            if (rsaPublic.getModulus().bitLength() < 2048
                    || !rsaPublic.getModulus().equals(rsaPrivate.getModulus())) {
                throw new IllegalArgumentException("RSA keys must match and have at least 2048 bits");
            }
            RSAKey key = new RSAKey.Builder(rsaPublic)
                    .privateKey(rsaPrivate)
                    .keyID(kid)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
            return new ImmutableJWKSet<>(new JWKSet(key));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load trusted signing key pair", exception);
        }
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
