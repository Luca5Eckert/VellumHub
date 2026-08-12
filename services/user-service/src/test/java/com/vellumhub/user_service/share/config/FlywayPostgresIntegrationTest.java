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
    void refusesToStartWhenTheMigratedSchemaBecomesIncompatible(@Autowired JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("alter table tb_users drop column email");

        assertThatThrownBy(() -> startApplication())
                .hasStackTraceContaining("Schema-validation");
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
                "JWT_KEY", "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLXdpdGgtYXQtbGVhc3QtMjU2LWJpdHM=",
                "JWT_EXPIRATION_MS", "3600000",
                "GOOGLE_CLIENT_ID", "test-client-id");
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
                Map.entry("jwt.secret", "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLXdpdGgtYXQtbGVhc3QtMjU2LWJpdHM="),
                Map.entry("jwt.expiration-ms", "3600000"),
                Map.entry("google.client.id", "test-client-id"),
                Map.entry("server.port", "0"));
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String table) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = ?)", Boolean.class, table));
    }

    private boolean indexExists(JdbcTemplate jdbcTemplate, String index) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select exists (select 1 from pg_indexes where schemaname = 'public' and indexname = ?)", Boolean.class, index));
    }
}
