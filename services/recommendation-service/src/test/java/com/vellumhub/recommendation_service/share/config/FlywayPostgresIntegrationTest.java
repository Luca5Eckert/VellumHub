package com.vellumhub.recommendation_service.share.config;

import com.vellumhub.recommendation_service.RecommendationServiceApplication;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ActiveProfiles("prod")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlywayPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg15").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("recommendation_migration_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        runtimeProperties().forEach((name, value) -> registry.add(name, () -> value));
    }

    @Test
    @Order(1)
    void startsAgainstAnEmptyPgvectorDatabaseAndAppliesTheInitialMigration(JdbcTemplate jdbcTemplate) {
        assertThat(jdbcTemplate.queryForObject("select count(*) from flyway_schema_history where version = '1' and success", Integer.class)).isEqualTo(1);
        assertThat(tableExists(jdbcTemplate, "book_features")).isTrue();
        assertThat(tableExists(jdbcTemplate, "user_profiles")).isTrue();
        assertThat(tableExists(jdbcTemplate, "recommendations")).isTrue();
        assertThat(jdbcTemplate.queryForObject("select count(*) from pg_extension where extname = 'vector'", Integer.class)).isEqualTo(1);
        assertThat(indexExists(jdbcTemplate, "idx_book_features_embedding_hnsw")).isTrue();
        assertThat(indexExists(jdbcTemplate, "idx_user_profiles_profile_vector_hnsw")).isTrue();
    }

    @Test
    @Order(2)
    void refusesToStartWhenTheMigratedSchemaBecomesIncompatible(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("alter table recommendations drop column title");

        assertThatThrownBy(() -> startApplication())
                .hasStackTraceContaining("Schema-validation");
    }

    private static ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(RecommendationServiceApplication.class)
                .profiles("prod")
                .web(WebApplicationType.NONE)
                .properties(runtimeProperties())
                .run();
    }

    private static Map<String, Object> runtimeProperties() {
        return Map.of(
                "spring.datasource.url", POSTGRES.getJdbcUrl(),
                "spring.datasource.username", POSTGRES.getUsername(),
                "spring.datasource.password", POSTGRES.getPassword(),
                "spring.kafka.bootstrap-servers", "localhost:65535",
                "spring.kafka.listener.auto-startup", "false",
                "spring.kafka.admin.fail-fast", "false",
                "management.health.kafka.enabled", "false",
                "jwt.secret", "test-secret-test-secret-test-secret-test-secret",
                "server.port", "0");
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String table) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = ?)", Boolean.class, table));
    }

    private boolean indexExists(JdbcTemplate jdbcTemplate, String index) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select exists (select 1 from pg_indexes where schemaname = 'public' and indexname = ?)", Boolean.class, index));
    }
}
