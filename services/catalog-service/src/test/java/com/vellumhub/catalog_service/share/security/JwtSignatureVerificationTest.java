package com.vellumhub.catalog_service.share.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the service's actual JWKS decoder and strict JWT access-token validators. */
class JwtSignatureVerificationTest {
    private static final String USER_ID = "93bb05c3-48a4-43b4-b789-410782cb0c9f";
    private static final String ISSUER = "https://auth.vellumhub.local";

    @Test
    void validatesAccessTokenProfileAndRejectsWrongKeysOrAlgorithms() throws Exception {
        KeyPair a = keyPair();
        KeyPair unrelated = keyPair();
        try (TestJwks jwks = new TestJwks(Map.of("key-a", a))) {
            JwtDecoder decoder = decoder(jwks.uri());
            assertThat(decode(decoder, signed(a, "key-a", "at+jwt", ISSUER, "api://vellumhub", false)))
                    .extracting(Jwt::getSubject).isEqualTo(USER_ID);
            assertThatThrownBy(() -> decode(decoder, signed(unrelated, "key-a", "at+jwt", ISSUER, "api://vellumhub", false)))
                    .isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> decode(decoder, signed(a, null, "at+jwt", ISSUER, "api://vellumhub", false)))
                    .isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> decode(decoder, signed(a, "unknown", "at+jwt", ISSUER, "api://vellumhub", false)))
                    .isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> decode(decoder, signed(a, "key-a", "JWT", ISSUER, "api://vellumhub", false)))
                    .isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> decode(decoder, signed(a, "key-a", "at+jwt", "https://wrong-issuer.invalid", "api://vellumhub", false)))
                    .isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> decode(decoder, signed(a, "key-a", "at+jwt", ISSUER, "api://other", false)))
                    .isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> decode(decoder, signed(a, "key-a", "at+jwt", ISSUER, "api://vellumhub", true)))
                    .isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> decode(decoder, legacyHs256()))
                    .isInstanceOf(JwtException.class);
        }
    }

    @Test
    void prepublicationAndRetirementOfKeysMaintainExpectedTrustBoundary() throws Exception {
        KeyPair a = keyPair();
        KeyPair b = keyPair();
        try (TestJwks jwks = new TestJwks(Map.of("key-a", a))) {
            JwtDecoder decoder = decoder(jwks.uri());
            String oldToken = signed(a, "key-a", "at+jwt", ISSUER, "api://vellumhub", false);
            String newToken = signed(b, "key-b", "at+jwt", ISSUER, "api://vellumhub", false);
            assertThat(decode(decoder, oldToken).getSubject()).isEqualTo(USER_ID);
            jwks.publish(Map.of("key-a", a, "key-b", b));
            assertThat(decode(decoder, newToken).getSubject()).isEqualTo(USER_ID);
            // Retirement only after the old token lifetime and cache propagation.
            jwks.publish(Map.of("key-b", b));
            JwtDecoder freshDecoder = decoder(jwks.uri());
            assertThat(decode(freshDecoder, newToken).getSubject()).isEqualTo(USER_ID);
            assertThatThrownBy(() -> decode(freshDecoder, oldToken)).isInstanceOf(JwtException.class);
            // A known, cached public key allows verification during a temporary JWKS outage.
            jwks.stop();
            assertThat(decode(freshDecoder, newToken).getSubject()).isEqualTo(USER_ID);
            assertThatThrownBy(() -> decode(freshDecoder, signed(a, "unknown", "at+jwt", ISSUER, "api://vellumhub", false)))
                    .isInstanceOf(JwtException.class);
        }
    }

    private JwtDecoder decoder(String jwksUri) {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "jwksUri", jwksUri);
        ReflectionTestUtils.setField(config, "issuer", ISSUER);
        ReflectionTestUtils.setField(config, "audience", "api://vellumhub");
        ReflectionTestUtils.setField(config, "clockSkewSeconds", 30L);
        return config.jwtDecoder();
    }

    private Jwt decode(JwtDecoder decoder, String token) {
        return decoder.decode(token);
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String signed(KeyPair key, String kid, String typ, String issuer,
                                 String audience, boolean expired) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(USER_ID)
                .audience(audience)
                .claim("client_id", "vellumhub-web")
                .claim("user_id", USER_ID)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now.minusSeconds(60)))
                .expirationTime(Date.from(now.plusSeconds(expired ? -300 : 300)))
                .build();
        SignedJWT token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType(typ)).keyID(kid).build(), claims);
        token.sign(new RSASSASigner((RSAPrivateKey) key.getPrivate()));
        return token.serialize();
    }

    private static String legacyHs256() throws Exception {
        SignedJWT token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(new JOSEObjectType("at+jwt")).keyID("key-a").build(),
                new JWTClaimsSet.Builder().subject(USER_ID).issueTime(new Date())
                        .expirationTime(Date.from(Instant.now().plusSeconds(300))).build());
        token.sign(new MACSigner(new byte[32]));
        return token.serialize();
    }

    private static final class TestJwks implements AutoCloseable {
        private final HttpServer server;
        private volatile String contents;

        TestJwks(Map<String, KeyPair> keys) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            publish(keys);
            server.createContext("/.well-known/jwks.json", exchange -> {
                byte[] response = contents.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Cache-Control", "public, max-age=60");
                exchange.sendResponseHeaders(200, response.length);
                try (var body = exchange.getResponseBody()) {
                    body.write(response);
                }
            });
            server.start();
        }

        String uri() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/.well-known/jwks.json";
        }

        void publish(Map<String, KeyPair> keys) {
            ArrayList<JWK> published = new ArrayList<>();
            keys.forEach((kid, key) -> published.add(
                    new RSAKey.Builder((RSAPublicKey) key.getPublic())
                            .keyID(kid).algorithm(JWSAlgorithm.RS256)
                            .keyUse(KeyUse.SIGNATURE).build()));
            contents = new JWKSet(published).toString();
        }

        void stop() {
            server.stop(0);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
