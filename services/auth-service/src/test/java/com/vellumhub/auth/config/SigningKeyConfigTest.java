package com.vellumhub.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class SigningKeyConfigTest {
    @TempDir
    Path temporary;

    @Test
    void refusesMissingSigningMaterial() {
        assertThatThrownBy(() -> new SigningKeyConfig().signingKeys("", "", "kid", "", ""))
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
        var config = new SigningKeyConfig();
        var source = config.jwkSource(config.signingKeys(
                privatePath.toString(), publicPath.toString(), "poc-key-1", "", ""));
        var jwks = source.get(new JWKSelector(new JWKMatcher.Builder().keyID("poc-key-1").build()), null);
        assertThat(jwks).hasSize(1);
        assertThat(jwks.getFirst().toPublicJWK().toJSONObject()).doesNotContainKey("d");
        assertThat(jwks.getFirst().getKeyID()).isEqualTo("poc-key-1");
    }

    @Test
    void publishesAnOverlapKeyWithoutUsingItForNewSignatures() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var active = generator.generateKeyPair();
        var previous = generator.generateKeyPair();
        var privatePath = Files.writeString(temporary.resolve("active-private.pem"),
                pem("PRIVATE KEY", active.getPrivate().getEncoded()));
        var publicPath = Files.writeString(temporary.resolve("active-public.pem"),
                pem("PUBLIC KEY", active.getPublic().getEncoded()));
        var overlapPath = Files.writeString(temporary.resolve("overlap-public.pem"),
                pem("PUBLIC KEY", previous.getPublic().getEncoded()));
        var config = new SigningKeyConfig();
        var material = config.signingKeys(privatePath.toString(), publicPath.toString(), "new",
                overlapPath.toString(), "old");
        var source = config.jwkSource(material);
        var published = source.get(new JWKSelector(new JWKMatcher.Builder().build()), null);
        assertThat(published).extracting(key -> key.getKeyID()).containsExactly("new", "old");
        assertThat(published).allMatch(key -> !key.isPrivate());
        var token = config.jwtEncoder(material).encode(JwtEncoderParameters.from(
                JwtClaimsSet.builder().subject("test-user").build()));
        var signed = SignedJWT.parse(token.getTokenValue());
        assertThat(signed.getHeader().getKeyID()).isEqualTo("new");
        assertThat(signed.verify(new RSASSAVerifier(published.getFirst().toRSAKey()))).isTrue();
        assertThat(signed.verify(new RSASSAVerifier(published.getLast().toRSAKey()))).isFalse();

        var oldPrivatePath = Files.writeString(temporary.resolve("old-private.pem"),
                pem("PRIVATE KEY", previous.getPrivate().getEncoded()));
        var oldMaterial = config.signingKeys(oldPrivatePath.toString(), overlapPath.toString(), "old", "", "");
        var oldToken = config.jwtEncoder(oldMaterial).encode(JwtEncoderParameters.from(
                JwtClaimsSet.builder().subject("test-user").build()));
        assertThat(SignedJWT.parse(oldToken.getTokenValue())
                .verify(new RSASSAVerifier(published.getLast().toRSAKey()))).isTrue();
        var retired = config.signingKeys(privatePath.toString(), publicPath.toString(), "new", "", "");
        assertThat(config.jwkSource(retired).get(
                new JWKSelector(new JWKMatcher.Builder().keyID("old").build()), null)).isEmpty();

        assertThatThrownBy(() -> config.signingKeys(privatePath.toString(), publicPath.toString(), "new",
                overlapPath.toString(), "new")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.signingKeys(privatePath.toString(), publicPath.toString(), "new",
                overlapPath.toString(), "")).isInstanceOf(IllegalStateException.class);
    }

    private static String pem(String kind, byte[] data) {
        return "-----BEGIN " + kind + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(data)
                + "\n-----END " + kind + "-----\n";
    }
}
