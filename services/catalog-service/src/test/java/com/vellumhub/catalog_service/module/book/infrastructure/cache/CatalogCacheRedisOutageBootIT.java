package com.vellumhub.catalog_service.module.book.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.catalog_service.module.book.application.service.BookService;
import com.vellumhub.catalog_service.module.book.presentation.dto.UpdateBookRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The complete Catalog Spring Boot application starts, reads and writes with
 * cache enabled while the configured Redis TCP endpoint refuses connections.
 * PostgreSQL remains the authority; Redis outage is not a readiness failure.
 */
@Testcontainers
@Tag("catalog-cache-e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CatalogCacheRedisOutageBootIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("catalog_cache_no_redis").withUsername("test").withPassword("test");
    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.kafka.bootstrap-servers", () -> "localhost:65535");
        r.add("spring.kafka.listener.auto-startup", () -> "false");
        r.add("spring.kafka.admin.fail-fast", () -> "false");
        r.add("management.health.kafka.enabled", () -> "false");
        r.add("jwt.public-key-location", () -> "classpath:security/test-public.pem");
        r.add("spring.data.redis.host", () -> "127.0.0.1");
        r.add("spring.data.redis.port", () -> "1"); // closed TCP port: no Redis server
        r.add("spring.data.redis.timeout", () -> "150ms");
        r.add("spring.data.redis.connect-timeout", () -> "150ms");
        r.add("vellumhub.cache.catalog.enabled", () -> "true");
        r.add("vellumhub.cache.catalog.invalidation-enabled", () -> "true");
        r.add("app.outbox.enabled", () -> "false");
    }

    @Autowired MockMvc http;
    @Autowired BookService books;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void startupAndAuthenticatedReadWriteRemainAvailableWithoutRedis() throws Exception {
        UUID id = new UUID(0L, 9123L);
        jdbc.update("""
                INSERT INTO books(id,title,description,release_year,author,isbn,page_count,
                                  publisher,version,created_at,updated_at)
                VALUES(?,?,?,2026,'Author','ISBN-9123',100,'Publisher',0,
                       '2026-01-01 00:00:00+00','2026-01-01 00:00:00+00')
                """, id, "Before", "Metadata");
        var reader = jwt().jwt(token -> token.claim("grant_type", "authorization_code"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("SCOPE_catalog:read"));
        var first = http.perform(get("/books/" + id).with(reader))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(first).path("title").asText()).isEqualTo("Before");

        books.update(id, new UpdateBookRequest("Updated", null, 2026, null,
                "Author", "ISBN-9123", 100, "Publisher", null));
        var second = http.perform(get("/books/" + id).with(reader))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(second).path("title").asText()).isEqualTo("Updated");
    }
}
