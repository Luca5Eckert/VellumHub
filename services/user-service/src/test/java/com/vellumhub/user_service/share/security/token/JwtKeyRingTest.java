package com.vellumhub.user_service.share.security.token;

import com.vellumhub.user_service.module.auth.infrastructure.security.token.JwtKeyRing;
import com.vellumhub.user_service.module.auth.infrastructure.security.token.JwksController;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeyRingTest {
    @Test
    void publishesOnlyPublicParametersWithStableKid() throws Exception {
        KeyPair pair = pair();
        var ring = keyRing("key-a", pair, pair);
        var result = new JwksController(ring).keys();
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        var jwk = (Map<?, ?>) ((java.util.List<?>) result.getBody().get("keys")).get(0);
        assertThat(jwk.get("kid")).isEqualTo("key-a");
        assertThat(jwk.get("kty")).isEqualTo("RSA");
        assertThat(jwk.get("alg")).isEqualTo("RS256");
        var names = jwk.keySet().stream().map(Object::toString).toList();
        assertThat(names).contains("n", "e", "kid", "alg", "kty")
                .doesNotContain("d", "p", "q", "dp", "dq", "qi", "oth");
    }

    @Test
    void deniesWrongKeyPairWeakKidOrUnknownExternalSources() throws Exception {
        KeyPair a = pair(), b = pair();
        assertThatThrownBy(() -> keyRing("key-a", a, b))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("do not match");
        assertThatThrownBy(() -> keyRing("bad kid", a, a))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtKeyRing("key-a", pem(a.getPrivate(), true),
                pem(a.getPublic(), false), "next=https://example.invalid/jwk.pem", new DefaultResourceLoader()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("local file");
    }

    private JwtKeyRing keyRing(String kid, KeyPair signing, KeyPair published) throws Exception {
        return new JwtKeyRing(kid, pem(signing.getPrivate(), true), pem(published.getPublic(), false),
                "", new DefaultResourceLoader());
    }

    private ByteArrayResource pem(Key key, boolean privateKey) {
        String tag = privateKey ? "PRIVATE KEY" : "PUBLIC KEY";
        String value = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key.getEncoded());
        return new ByteArrayResource(("-----BEGIN " + tag + "-----\n" + value
                + "\n-----END " + tag + "-----\n").getBytes(StandardCharsets.US_ASCII));
    }

    private KeyPair pair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }
}
