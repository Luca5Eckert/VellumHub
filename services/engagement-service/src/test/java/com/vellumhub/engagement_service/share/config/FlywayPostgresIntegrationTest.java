package com.vellumhub.engagement_service.share.config;

import com.vellumhub.engagement_service.EngagementServiceApplication;
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
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.vellumhub.engagement_service.module.reaction.domain.port.ReactionRepository;
import com.vellumhub.engagement_service.module.reaction.domain.model.TypeReaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.awaitility.Awaitility.await;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ActiveProfiles("prod")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlywayPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("engagement_migration_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        runtimeProperties().forEach((name, value) -> registry.add(name, () -> value));
    }

    @Test
    @Order(1)
    void startsAgainstAnEmptyPostgresDatabaseAndAppliesAllMigrations(@Autowired JdbcTemplate jdbcTemplate) {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version in ('1', '2', '3') and success",
                Integer.class
        )).isEqualTo(3);
        assertThat(tableExists(jdbcTemplate, "book_snapshot")).isTrue();
        assertThat(tableExists(jdbcTemplate, "rating")).isTrue();
        assertThat(tableExists(jdbcTemplate, "reactions")).isTrue();
        assertThat(tableExists(jdbcTemplate, "reading_session_entries")).isTrue();
        assertThat(indexExists(jdbcTemplate, "idx_rating_user_id")).isTrue();
        assertThat(columnExists(jdbcTemplate, "reactions", "created_at")).isTrue();
        assertThat(columnExists(jdbcTemplate, "reactions", "updated_at")).isTrue();
        assertThat(columnIsNullable(jdbcTemplate, "reactions", "created_at")).isFalse();
        assertThat(columnIsNullable(jdbcTemplate, "reactions", "updated_at")).isFalse();
    }

    @Test
    @Order(99)
    void refusesToStartWhenTheMigratedSchemaBecomesIncompatible(@Autowired JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("alter table rating drop column stars");

        assertThatThrownBy(() -> startApplication())
                .hasStackTraceContaining("Schema");
    }

    @Test
    @Order(2)
    void concurrentReactionUpdatesReadTheLastCommittedType(
            @Autowired JdbcTemplate jdbc,
            @Autowired ReactionRepository reactions,
            @Autowired PlatformTransactionManager transactionManager
    ) throws Exception {
        UUID owner = UUID.randomUUID();
        long reactionId = -292L;
        jdbc.update("insert into reactions (id, user_id, type_reaction, created_at, updated_at) "
                + "values (?, ?, 'POSITIVE', current_timestamp, current_timestamp)", reactionId, owner);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> transaction.execute(status -> {
                var reaction = reactions.findByIdForUpdate(reactionId).orElseThrow();
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release reaction lock");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                TypeReaction previous = reaction.updateType(TypeReaction.VERY_POSITIVE, owner);
                reactions.save(reaction);
                return previous;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> transaction.execute(status -> {
                var reaction = reactions.findByIdForUpdate(reactionId).orElseThrow();
                TypeReaction previous = reaction.updateType(TypeReaction.NEGATIVE, owner);
                reactions.save(reaction);
                return previous;
            }));
            // Observe a real PostgreSQL lock wait, rather than relying on thread timing.
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject("select count(*) from pg_stat_activity "
                            + "where datname = current_database() and wait_event_type = 'Lock' "
                            + "and query like '%reactions%'", Integer.class)).isPositive());
            assertThat(second.isDone()).isFalse();
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(TypeReaction.POSITIVE);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(TypeReaction.VERY_POSITIVE);
            assertThat(reactions.findById(reactionId).orElseThrow().getTypeReaction())
                    .isEqualTo(TypeReaction.NEGATIVE);
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            jdbc.update("delete from reactions where id = ?", reactionId);
        }
    }

    private static ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(EngagementServiceApplication.class)
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
                "JWT_KEY", "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLXdpdGgtYXQtbGVhc3QtMjU2LWJpdHM=");
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
                "jwt.secret", "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLXdpdGgtYXQtbGVhc3QtMjU2LWJpdHM=",
                "server.port", "0");
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String table) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = ?)",
                Boolean.class,
                table
        ));
    }

    private boolean indexExists(JdbcTemplate jdbcTemplate, String index) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists (select 1 from pg_indexes where schemaname = 'public' and indexname = ?)",
                Boolean.class,
                index
        ));
    }

    private boolean columnExists(JdbcTemplate jdbcTemplate, String table, String column) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists (select 1 from information_schema.columns where table_schema = 'public' and table_name = ? and column_name = ?)",
                Boolean.class,
                table,
                column
        ));
    }

    private boolean columnIsNullable(JdbcTemplate jdbcTemplate, String table, String column) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select is_nullable = 'YES' from information_schema.columns where table_schema = 'public' and table_name = ? and column_name = ?",
                Boolean.class,
                table,
                column
        ));
    }
}
