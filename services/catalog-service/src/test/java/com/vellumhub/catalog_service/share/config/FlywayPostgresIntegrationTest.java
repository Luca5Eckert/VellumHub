package com.vellumhub.catalog_service.share.config;

import com.vellumhub.catalog_service.CatalogServiceApplication;
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
            .withDatabaseName("catalog_migration_test")
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
        assertThat(tableExists(jdbcTemplate, "genres")).isTrue();
        assertThat(tableExists(jdbcTemplate, "books")).isTrue();
        assertThat(tableExists(jdbcTemplate, "book_progress")).isTrue();
        assertThat(indexExists(jdbcTemplate, "idx_book_progress_user_id")).isTrue();
    }


    @Test
    @Order(2)
    void optimisticLockRejectsStaleReadingProgress(
            @Autowired JdbcTemplate jdbcTemplate,
            @Autowired jakarta.persistence.EntityManagerFactory factory) {
        java.util.UUID bookId = java.util.UUID.randomUUID();
        java.util.UUID userId = java.util.UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO books(id,title,release_year,author,isbn,page_count,publisher,version,created_at)
            VALUES(?,?,?,?,?,?,?,0,now())
            """, bookId,"Optimistic Reading",2026,"Test Author","test-isbn",300,"Test Publisher");

        var setup = factory.createEntityManager();
        java.util.UUID progressId;
        try {
            setup.getTransaction().begin();
            var book = setup.find(com.vellumhub.catalog_service.module.book.domain.model.Book.class,bookId);
            var progress = com.vellumhub.catalog_service.module.book_progress.domain.model.BookProgress.create(
                    userId,book,0,
                    com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus.READING,
                    null,null);
            setup.persist(progress);
            setup.getTransaction().commit();
            progressId=progress.getId();
        } finally { setup.close(); }

        var first = factory.createEntityManager();
        var stale = factory.createEntityManager();
        try {
            first.getTransaction().begin();
            stale.getTransaction().begin();
            var firstProgress = first.find(
                    com.vellumhub.catalog_service.module.book_progress.domain.model.BookProgress.class,progressId);
            var staleProgress = stale.find(
                    com.vellumhub.catalog_service.module.book_progress.domain.model.BookProgress.class,progressId);
            firstProgress.update(
                    com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus.READING,10);
            first.getTransaction().commit();
            staleProgress.update(
                    com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus.READING,20);
            assertThatThrownBy(() -> stale.getTransaction().commit())
                    .isInstanceOfAny(jakarta.persistence.OptimisticLockException.class,
                                     jakarta.persistence.RollbackException.class);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT current_page FROM book_progress WHERE id=?",Integer.class,progressId))
                    .isEqualTo(10);
        } finally {
            if (first.getTransaction().isActive()) first.getTransaction().rollback();
            if (stale.getTransaction().isActive()) stale.getTransaction().rollback();
            first.close(); stale.close();
        }
    }

    @Test
    @Order(3)
    void refusesToStartWhenTheMigratedSchemaBecomesIncompatible(@Autowired JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("alter table books drop column title");

        assertThatThrownBy(() -> startApplication())
                .hasStackTraceContaining("Schema");
    }

    private static ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(CatalogServiceApplication.class)
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
                "JWT_PUBLIC_KEY_LOCATION", "classpath:security/test-public.pem");
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
                "jwt.public-key-location", "classpath:security/test-public.pem",
                "server.port", "0");
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String table) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = ?)", Boolean.class, table));
    }

    private boolean indexExists(JdbcTemplate jdbcTemplate, String index) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select exists (select 1 from pg_indexes where schemaname = 'public' and indexname = ?)", Boolean.class, index));
    }
}
