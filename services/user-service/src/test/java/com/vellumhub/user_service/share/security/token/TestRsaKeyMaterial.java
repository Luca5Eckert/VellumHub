package com.vellumhub.user_service.share.security.token;

import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/** RSA material generated only for tests; no signing key is stored in the repository. */
public final class TestRsaKeyMaterial {
    private static final KeyPair KEYS = createKeyPair();
    private static final Path PUBLIC_FILE = writeTemporaryPem("PUBLIC KEY", KEYS.getPublic().getEncoded());
    private static final Path PRIVATE_FILE = writeTemporaryPem("PRIVATE KEY", KEYS.getPrivate().getEncoded());

    private TestRsaKeyMaterial() {}

    public static RSAPublicKey publicKey() {
        return (RSAPublicKey) KEYS.getPublic();
    }

    public static RSAPrivateKey privateKey() {
        return (RSAPrivateKey) KEYS.getPrivate();
    }

    public static ByteArrayResource privateResource() {
        return new ByteArrayResource(pem("PRIVATE KEY", KEYS.getPrivate().getEncoded())
                .getBytes(StandardCharsets.US_ASCII));
    }

    public static String publicLocation() {
        return PUBLIC_FILE.toUri().toString();
    }

    public static String privateLocation() {
        return PRIVATE_FILE.toUri().toString();
    }

    private static KeyPair createKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create test RSA key pair", exception);
        }
    }

    private static Path writeTemporaryPem(String type, byte[] key) {
        try {
            Path path = Files.createTempFile("vellumhub-jwt-test-", ".pem");
            Files.writeString(path, pem(type, key), StandardCharsets.US_ASCII);
            path.toFile().deleteOnExit();
            return path;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create test-only RSA PEM", exception);
        }
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
    }
}
