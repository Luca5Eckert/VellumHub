package com.vellumhub.user_service.module.auth.infrastructure.security.token;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit, externally provisioned RSA keyring. Only the User Service can load the signing key.
 * Extra keys are public-only, intended for prepublication and overlap during rotation.
 */
@Component
public final class JwtKeyRing {
    private static final int MIN_RSA_BITS = 2048;
    private static final int MAX_PUBLIC_KEYS = 8;
    private final String activeKid;
    private final RSAPrivateKey privateKey;
    private final Map<String, RSAPublicKey> publicKeys;

    public JwtKeyRing(@Value("${jwt.active-kid:local-key-1}") String activeKid,
                      @Value("${jwt.private-key-location}") Resource signingKey,
                      @Value("${jwt.public-key-location}") Resource activePublicKey,
                      @Value("${jwt.extra-public-keys:}") String extraPublicKeys,
                      ResourceLoader resources) throws IOException {
        validateKid(activeKid);
        this.activeKid = activeKid;
        this.privateKey = readPrivate(signingKey);
        Map<String, RSAPublicKey> keys = new LinkedHashMap<>();
        keys.put(activeKid, readPublic(activePublicKey));
        if (!extraPublicKeys.isBlank()) {
            for (String item : extraPublicKeys.split(",", -1)) {
                String[] entry = item.trim().split("=", 2);
                if (entry.length != 2 || entry[1].isBlank()) {
                    throw new IllegalArgumentException("jwt.extra-public-keys must use kid=resource-location");
                }
                String kid = entry[0].trim();
                validateKid(kid);
                if (!entry[1].trim().startsWith("file:")) {
                    throw new IllegalArgumentException("Additional public JWK sources must be local file resources");
                }
                if (keys.putIfAbsent(kid, readPublic(resources.getResource(entry[1].trim()))) != null) {
                    throw new IllegalArgumentException("Duplicate JWT kid: " + kid);
                }
            }
        }
        if (keys.size() > MAX_PUBLIC_KEYS) {
            throw new IllegalArgumentException("JWT JWKS exceeds maximum key count");
        }
        this.publicKeys = Collections.unmodifiableMap(keys);
        validatePair(privateKey, publicKeys.get(activeKid));
    }

    public String activeKid() {
        return activeKid;
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    /** Only public parameters are ever serialized. No private-key material reaches this endpoint. */
    public List<Map<String, Object>> publicJwks() {
        List<Map<String, Object>> jwks = new ArrayList<>();
        publicKeys.forEach((kid, publicKey) -> jwks.add(
                new RSAKey.Builder(publicKey).keyID(kid).algorithm(JWSAlgorithm.RS256)
                        .keyUse(KeyUse.SIGNATURE).build().toPublicJWK().toJSONObject()));
        return List.copyOf(jwks);
    }

    private static void validateKid(String kid) {
        if (kid == null || !kid.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("JWT kid must be a stable 1-64 character identifier");
        }
    }

    private static RSAPrivateKey readPrivate(Resource resource) throws IOException {
        try (InputStream stream = resource.getInputStream()) {
            RSAPrivateKey key = Objects.requireNonNull(RsaKeyConverters.pkcs8().convert(stream),
                    "Invalid PKCS#8 JWT signing key");
            if (key.getModulus().bitLength() < MIN_RSA_BITS) {
                throw new IllegalArgumentException("JWT signing key must be at least 2048 bits");
            }
            return key;
        }
    }

    private static RSAPublicKey readPublic(Resource resource) throws IOException {
        try (InputStream stream = resource.getInputStream()) {
            RSAPublicKey key = Objects.requireNonNull(RsaKeyConverters.x509().convert(stream),
                    "Invalid X.509 JWT public key");
            if (key.getModulus().bitLength() < MIN_RSA_BITS) {
                throw new IllegalArgumentException("JWT verification key must be at least 2048 bits");
            }
            return key;
        }
    }

    private static void validatePair(RSAPrivateKey privateKey, RSAPublicKey publicKey) {
        try {
            byte[] probe = "vellumhub-jwt-key-pair-check".getBytes(StandardCharsets.UTF_8);
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(probe);
            byte[] signature = signer.sign();
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(probe);
            if (!verifier.verify(signature)) {
                throw new IllegalArgumentException("Active JWT signing key and public key do not match");
            }
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("Could not validate active JWT key pair", error);
        }
    }
}
