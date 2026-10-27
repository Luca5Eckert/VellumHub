package com.vellumhub.user_service.share.config;

import com.vellumhub.user_service.UserServiceApplication;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ActiveProfiles("prod")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlywayPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("user_migration_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        runtimeProperties().forEach((name, value) -> registry.add(name, () -> value));
    }

    @Test
    @Order(1)
    void startsAgainstAnEmptyPostgresDatabaseAndAppliesTheInitialMigration(@Autowired JdbcTemplate jdbcTemplate) {
        assertThat(jdbcTemplate.queryForObject("select count(*) from flyway_schema_history where version = '1' and success", Integer.class)).isEqualTo(1);
        assertThat(tableExists(jdbcTemplate, "tb_users")).isTrue();
        assertThat(tableExists(jdbcTemplate, "user_preferences")).isTrue();
        assertThat(tableExists(jdbcTemplate, "user_preference_genres")).isTrue();
        assertThat(indexExists(jdbcTemplate, "uk_tb_users_email")).isTrue();
    }


    @Test
    @Order(2)
    void provisionsAuthOwnedUuidV7IdempotentlyWithoutCredentialColumns(@Autowired JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '4' and success", Integer.class))
                .isEqualTo(1);
        var endpoint = new com.vellumhub.user_service.module.user.infrastructure.provisioning
                .ProfileProvisioningController(jdbc, "local-integration-secret-of-32-chars");
        var id = java.util.UUID.fromString("019a0000-1111-7000-8000-000000000001");
        var value = new com.vellumhub.user_service.module.user.infrastructure.provisioning
                .ProfileProvisioningController.NewProfile(id, "Ada", "ada@example.org");
        assertThat(endpoint.create("local-integration-secret-of-32-chars", value)
                .getStatusCode().value()).isEqualTo(201);
        assertThat(endpoint.create("local-integration-secret-of-32-chars", value)
                .getStatusCode().value()).isEqualTo(204);
        assertThat(columnExists(jdbc, "tb_users", "password")).isFalse();
        assertThat(tableExists(jdbc, "external_identities")).isFalse();
        assertThat(tableExists(jdbc, "external_identity_audit")).isFalse();
        assertThat(jdbc.queryForObject("select role from tb_users where id = ?",
                String.class, id)).isEqualTo("USER");
        var colliding = new com.vellumhub.user_service.module.user.infrastructure.provisioning
                .ProfileProvisioningController.NewProfile(
                    java.util.UUID.fromString("019a0000-1111-7000-8000-000000000002"),
                    "Other", "ada@example.org");
        assertThat(endpoint.create("local-integration-secret-of-32-chars", colliding)
                .getStatusCode().value()).isEqualTo(409);
        assertThat(jdbc.queryForObject(
                "select count(*) from tb_users where lower(email) = 'ada@example.org'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @Order(3)
    void refusesToStartWhenTheMigratedSchemaBecomesIncompatible(@Autowired JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("alter table tb_users drop column email");

        assertThatThrownBy(() -> startApplication())
                .hasStackTraceContaining("Schema");
    }

    private static ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(UserServiceApplication.class)
                .profiles("prod")
                .web(WebApplicationType.SERVLET)
                .properties(startupProperties())
                .run();
    }

    private static Map<String, Object> startupProperties() {
        return Map.of(
                "SPRING_DATASOURCE_URL", POSTGRES.getJdbcUrl(),
                "SPRING_DATASOURCE_USERNAME", POSTGRES.getUsername(),
                "SPRING_DATASOURCE_PASSWORD", POSTGRES.getPassword(),
                "KAFKA_BOOTSTRAP_SERVERS", "localhost:65535",
                "JWT_JWKS_URI", "http://127.0.0.1:1/oauth2/jwks");
    }
    private static Map<String, Object> runtimeProperties() {
        return Map.ofEntries(
                Map.entry("spring.datasource.url", POSTGRES.getJdbcUrl()),
                Map.entry("spring.datasource.username", POSTGRES.getUsername()),
                Map.entry("spring.datasource.password", POSTGRES.getPassword()),
                Map.entry("spring.kafka.bootstrap-servers", "localhost:65535"),
                Map.entry("spring.kafka.listener.auto-startup", "false"),
                Map.entry("spring.kafka.admin.fail-fast", "false"),
                Map.entry("management.health.kafka.enabled", "false"),
                Map.entry("JWT_JWKS_URI", "http://127.0.0.1:1/oauth2/jwks"),
                Map.entry("server.port", "0"));
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String table) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = ?)", Boolean.class, table));
    }

    private boolean columnExists(JdbcTemplate jdbcTemplate, String table, String column) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists (select 1 from information_schema.columns where table_schema = 'public' and table_name = ? and column_name = ?)",
                Boolean.class, table, column));
    }

    private boolean indexExists(JdbcTemplate jdbcTemplate, String index) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select exists (select 1 from pg_indexes where schemaname = 'public' and indexname = ?)", Boolean.class, index));
    }
}
