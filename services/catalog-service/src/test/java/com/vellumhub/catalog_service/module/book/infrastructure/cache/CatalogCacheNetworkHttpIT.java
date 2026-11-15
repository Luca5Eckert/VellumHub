package com.vellumhub.catalog_service.module.book.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.vellumhub.catalog_service.module.book.application.service.BookService;
import com.vellumhub.catalog_service.module.book.presentation.dto.UpdateBookRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Actual localhost TCP HTTP and a real RS256 JWT/JWKS security chain.
 * NOT a production load test: PostgreSQL/Redis and HTTP use local Testcontainers.
 */
@Testcontainers
@Tag("catalog-cache-e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogCacheNetworkHttpIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("catalog_http_real").withUsername("test").withPassword("test");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--save", "", "--appendonly", "no",
                    "--maxmemory", "128mb", "--maxmemory-policy", "noeviction");
    private static KeyPair signing;
    private static HttpServer jwks;
    static {
        try {
            POSTGRES.start();
            REDIS.start();
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            signing = generator.generateKeyPair();
            var key = new RSAKey.Builder((RSAPublicKey) signing.getPublic())
                    .keyID("catalog-integration-key").build();
            byte[] body = new JWKSet(key).toString().getBytes(StandardCharsets.UTF_8);
            jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            jwks.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
            });
            jwks.start();
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.kafka.bootstrap-servers", () -> "localhost:65535");
        r.add("spring.kafka.listener.auto-startup", () -> "false");
        r.add("spring.kafka.admin.fail-fast", () -> "false");
        r.add("management.health.kafka.enabled", () -> "false");
        r.add("JWT_JWKS_URI", () -> "http://127.0.0.1:" + jwks.getAddress().getPort() + "/jwks");
        r.add("JWT_ISSUER", () -> "https://auth.vellumhub.local");
        r.add("JWT_AUDIENCE", () -> "api://vellumhub");
        r.add("jwt.public-key-location", () -> "classpath:security/test-public.pem");
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("vellumhub.cache.catalog.enabled", () -> "true");
        r.add("vellumhub.cache.catalog.invalidation-enabled", () -> "true");
        r.add("app.outbox.enabled", () -> "false");
    }

    @AfterAll
    static void stopJwks() {
        jwks.stop(0);
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired BookService books;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private String signedAccessToken(String scope) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .issuer("https://auth.vellumhub.local")
                .audience("api://vellumhub")
                .subject(UUID.randomUUID().toString())
                .issueTime(Date.from(now.minusSeconds(10)))
                .expirationTime(Date.from(now.plusSeconds(600)))
                .jwtID(UUID.randomUUID().toString())
                .claim("client_id", "catalog-cache-http-it")
                .claim("grant_type", "authorization_code")
                .claim("scope", scope)
                .claim("roles", List.of("USER"))
                .build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("at+jwt"))
                .keyID("catalog-integration-key").build(), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) signing.getPrivate()));
        return jwt.serialize();
    }

    private HttpResponse<String> read(UUID id, String bearer) throws Exception {
        var builder = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/books/" + id)).GET();
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void signedJwtAuthorizationRealHttpHitsAndAfterCommitFreshness() throws Exception {
        UUID id = new UUID(0L, 8191L);
        jdbc.update("""
                INSERT INTO books(id,title,description,release_year,author,isbn,page_count,
                                  publisher,version,created_at,updated_at)
                VALUES(?,?,?,2026,'Author','ISBN-8191',100,'Publisher',0,
                       '2026-01-01 00:00:00+00','2026-01-01 00:00:00+00')
                """, id, "Initial", "Metadata");
        assertThat(read(id, null).statusCode()).isEqualTo(401);
        assertThat(read(id, signedAccessToken("catalog:write")).statusCode()).isEqualTo(403);

        String bearer = signedAccessToken("catalog:read");
        var first = read(id, bearer);
        var warmed = read(id, bearer);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(warmed.statusCode()).isEqualTo(200);
        assertThat(warmed.body()).isEqualTo(first.body());
        assertThat(json.readTree(first.body()).path("title").asText()).isEqualTo("Initial");

        books.update(id, new UpdateBookRequest("New metadata", null, 2026, null,
                "Author", "ISBN-8191", 100, "Publisher", null));
        var refreshed = read(id, bearer);
        assertThat(refreshed.statusCode()).isEqualTo(200);
        assertThat(json.readTree(refreshed.body()).path("title").asText()).isEqualTo("New metadata");
        books.delete(id);
        assertThat(read(id, bearer).statusCode()).isEqualTo(404);
    }
}
