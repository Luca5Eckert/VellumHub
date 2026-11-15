package com.vellumhub.catalog_service.module.book.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.catalog_service.CatalogServiceApplication;
import com.vellumhub.catalog_service.module.book.application.service.BookService;
import com.vellumhub.catalog_service.module.book.presentation.dto.UpdateBookRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two separately bootstrapped Catalog application contexts with independent
 * Spring Data Redis connections and the same real PG+Redis containers.
 * Tests actual BookService -> OutboxBookChangeWriter -> afterCommit invalidation.
 * This is not a cross-process or TLS/network load benchmark.
 */
@Testcontainers
@Tag("catalog-cache-e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CatalogCacheTwoContextsIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("catalog_two_contexts").withUsername("test").withPassword("test");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--save", "", "--appendonly", "no",
                    "--maxmemory", "128mb", "--maxmemory-policy", "noeviction");
    static { POSTGRES.start(); REDIS.start(); }

    @DynamicPropertySource
    static void firstProperties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.kafka.bootstrap-servers", () -> "localhost:65535");
        r.add("spring.kafka.listener.auto-startup", () -> "false");
        r.add("spring.kafka.admin.fail-fast", () -> "false");
        r.add("management.health.kafka.enabled", () -> "false");
        r.add("jwt.public-key-location", () -> "classpath:security/test-public.pem");
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("vellumhub.cache.catalog.enabled", () -> "true");
        r.add("vellumhub.cache.catalog.invalidation-enabled", () -> "true");
        r.add("app.outbox.enabled", () -> "false");
    }

    @Autowired MockMvc firstHttp;
    @Autowired JdbcTemplate jdbc;
    @Autowired BookReadCache firstCache;

    private final ObjectMapper json = new ObjectMapper();
    private ConfigurableApplicationContext secondContext;
    private MockMvc secondHttp;

    @BeforeAll
    void bootSecondCatalogWithReadsOffAndInvalidationOn() {
        secondContext = new SpringApplicationBuilder(CatalogServiceApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=0",
                        "spring.flyway.enabled=false",
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "spring.kafka.bootstrap-servers=localhost:65535",
                        "spring.kafka.listener.auto-startup=false",
                        "spring.kafka.admin.fail-fast=false",
                        "management.health.kafka.enabled=false",
                        "jwt.public-key-location=classpath:security/test-public.pem",
                        "spring.data.redis.host=" + REDIS.getHost(),
                        "spring.data.redis.port=" + REDIS.getMappedPort(6379),
                        "vellumhub.cache.catalog.enabled=false",
                        "vellumhub.cache.catalog.invalidation-enabled=true",
                        "app.outbox.enabled=false"
                ).run(
                        // CLI properties override application.properties; builder
                        // defaults are not sufficient for these feature flags.
                        "--vellumhub.cache.catalog.enabled=false",
                        "--vellumhub.cache.catalog.invalidation-enabled=true",
                        "--spring.data.redis.host=" + REDIS.getHost(),
                        "--spring.data.redis.port=" + REDIS.getMappedPort(6379));
        secondHttp = MockMvcBuilders.webAppContextSetup((WebApplicationContext) secondContext)
                .apply(springSecurity()).build();
        assertThat(secondContext.getBean(BookReadCache.class)).isNotSameAs(firstCache);
        assertThat(secondContext.getEnvironment().getProperty(
                "vellumhub.cache.catalog.invalidation-enabled")).isEqualTo("true");
        assertThat(secondContext.getEnvironment().getProperty(
                "spring.data.redis.port")).isEqualTo(REDIS.getMappedPort(6379).toString());
        assertThat(secondContext.getBean(StringRedisTemplate.class)).isNotNull();
    }

    @AfterAll
    void stopSecondCatalog() {
        if (secondContext != null) secondContext.close();
    }

    private RequestPostProcessor reader() {
        return jwt().jwt(token -> token.claim("grant_type", "authorization_code"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("SCOPE_catalog:read"));
    }

    private String detail(MockMvc http, UUID bookId) throws Exception {
        var response = http.perform(get("/books/" + bookId).with(reader()))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("title").asText();
    }

    private String pageTitle(MockMvc http) throws Exception {
        var response = http.perform(get("/books?pageNumber=0&pageSize=10").with(reader()))
                .andExpect(status().isOk()).andReturn().getResponse();
        var page = json.readTree(response.getContentAsString());
        assertThat(page.size()).isEqualTo(1);
        return page.get(0).path("title").asText();
    }

    @Test
    void crossContextUpdateRollbackAndDeleteRespectCommittedOutboxState() throws Exception {
        UUID id = new UUID(0, 9001);
        jdbc.update("""
                INSERT INTO books(id,title,description,release_year,author,isbn,page_count,
                                  publisher,version,created_at,updated_at)
                VALUES(?,?,?,2026,'Author','ISBN-9001',100,'Publisher',0,
                       '2026-01-01 00:00:00+00','2026-01-01 00:00:00+00')
                """, id, "Before", "Metadata");
        assertThat(detail(firstHttp, id)).isEqualTo("Before");
        assertThat(pageTitle(firstHttp)).isEqualTo("Before");

        BookService writer = secondContext.getBean(BookService.class);
        writer.update(id, new UpdateBookRequest("Committed", null, 2026, null,
                "Author", "ISBN-9001", 100, "Publisher", null));
        // Distinguish a failed writer update from a missed cross-context bump.
        assertThat(jdbc.queryForObject("SELECT title FROM books WHERE id=?",
                String.class, id)).isEqualTo("Committed");
        // A reader on the other application context must not see old JSON.
        assertThat(detail(firstHttp, id)).isEqualTo("Committed");
        assertThat(pageTitle(firstHttp)).isEqualTo("Committed");

        TransactionTemplate tx = new TransactionTemplate(
                secondContext.getBean(PlatformTransactionManager.class));
        assertThatThrownBy(() -> tx.execute(status -> {
            writer.update(id, new UpdateBookRequest("RolledBack", null, 2026, null,
                    "Author", "ISBN-9001", 100, "Publisher", null));
            throw new IllegalStateException("Intentional rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(detail(firstHttp, id)).isEqualTo("Committed");
        assertThat(pageTitle(firstHttp)).isEqualTo("Committed");

        // Same data path, different Spring context and separate Redis client.
        assertThat(detail(secondHttp, id)).isEqualTo("Committed");
        writer.delete(id);
        firstHttp.perform(get("/books/" + id).with(reader()))
                .andExpect(status().isNotFound());
        var pageResponse = firstHttp.perform(get("/books?pageNumber=0&pageSize=10").with(reader()))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(json.readTree(pageResponse.getContentAsString()).size()).isZero();
    }
}
