package com.vellumhub.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class SigningKeyConfigTest {
    @TempDir
    Path temporary;

    @Test
    void refusesMissingSigningMaterial() {
        assertThatThrownBy(() -> new SigningKeyConfig().jwkSource("", "", "kid"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void publishesAStableKidWithPublicOnlyMaterial() throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(2048);
        var pair = keys.generateKeyPair();
        var privatePath = temporary.resolve("private.pem");
        var publicPath = temporary.resolve("public.pem");
        Files.writeString(privatePath, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        Files.writeString(publicPath, pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        var source = new SigningKeyConfig().jwkSource(
                privatePath.toString(), publicPath.toString(), "poc-key-1");
        var jwks = source.get(new JWKSelector(new JWKMatcher.Builder().keyID("poc-key-1").build()), null);
        assertThat(jwks).hasSize(1);
        assertThat(jwks.getFirst().toPublicJWK().toJSONObject()).doesNotContainKey("d");
        assertThat(jwks.getFirst().getKeyID()).isEqualTo("poc-key-1");
    }

    private static String pem(String kind, byte[] data) {
        return "-----BEGIN " + kind + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(data)
                + "\n-----END " + kind + "-----\n";
    }
}
