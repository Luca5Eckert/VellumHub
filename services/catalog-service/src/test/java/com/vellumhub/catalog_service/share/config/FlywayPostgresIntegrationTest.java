package com.vellumhub.catalog_service.share.config;

import com.vellumhub.catalog_service.module.book_progress.application.handler.BookReadingsHandler;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookProgressConflictException;
import com.vellumhub.catalog_service.CatalogServiceApplication;
import com.vellumhub.catalog_service.module.book.application.query.PageBook;
import com.vellumhub.catalog_service.module.book.application.service.BookService;
import com.vellumhub.catalog_service.module.book.domain.exception.BookDomainException;
import com.vellumhub.catalog_service.module.book.presentation.dto.GetBookResponse;
import com.vellumhub.catalog_service.module.book_progress.application.handler.UpdateBookProgressHandler;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookIsNotBeingReadException;
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
    @Order(4)
    void updatesOnlyTheActiveReadingAndEmitsItsEvent(
            @Autowired JdbcTemplate jdbc, @Autowired UpdateBookProgressHandler progress,
            @Autowired BookReadingsHandler readings) {
        UUID book = UUID.randomUUID();
        UUID reader = UUID.randomUUID();
        UUID completed = UUID.randomUUID();
        UUID active = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID otherReader = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO books(id,title,release_year,author,isbn,page_count,publisher,version,created_at)
                VALUES(?,'Rereading',2026,'Test Author','rereading-test',300,'Test Publisher',0,now())
                """, book);
        jdbc.update("""
                INSERT INTO book_progress(id,book_id,user_id,reading_status,current_page,is_active,version,created_at)
                VALUES (?,?,?,'COMPLETED',300,false,0,now() - interval '2 days'),
                       (?,?,?,'READING',20,true,0,now() - interval '1 day'),
                       (?,?,?,'READING',15,true,0,now())
                """, completed, book, reader, active, book, reader, other, book, otherReader);

        progress.handle(42, book, reader);

        assertThat(jdbc.queryForObject("SELECT current_page FROM book_progress WHERE id=?", Integer.class, active)).isEqualTo(42);
        assertThat(jdbc.queryForObject("SELECT current_page FROM book_progress WHERE id=?", Integer.class, completed)).isEqualTo(300);
        assertThat(jdbc.queryForObject("SELECT current_page FROM book_progress WHERE id=?", Integer.class, other)).isEqualTo(15);
        assertThat(jdbc.queryForList("""
                SELECT aggregate_id FROM outbox_events WHERE aggregate_id IN (?, ?, ?)
                """, UUID.class, active, completed, other)).containsExactly(active);

        assertThatThrownBy(() -> readings.start(book, reader)).isInstanceOf(BookProgressConflictException.class);

        progress.handle(300, book, reader);
        assertThatThrownBy(() -> progress.handle(50, book, reader)).isInstanceOf(BookIsNotBeingReadException.class);

        var rereading = readings.start(book, reader);
        UUID rereadingId = rereading.progress().getId();
        assertThat(rereading.readingNumber()).isEqualTo(3);
        assertThat(rereading.progress().getCurrentPage()).isZero();
        assertThat(readings.list(book, reader))
                .extracting(reading -> reading.progress().getId())
                .containsExactly(rereadingId, active, completed);
        assertThat(readings.list(book, otherReader))
                .extracting(reading -> reading.progress().getId())
                .containsExactly(other);
        assertThat(jdbc.queryForList("SELECT aggregate_version FROM outbox_events WHERE aggregate_id = ?",
                Long.class, rereadingId)).containsExactly(0L);
    }

    @Test
    @Order(3)
    void searchesVisibleBooksByRelevanceWithStablePages(
            @Autowired JdbcTemplate jdbc, @Autowired BookService books) {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID hidden = UUID.randomUUID();
        insertSearchBook(jdbc, first, "Nebulosa", "A. Silva", "Uma expedição", false);
        insertSearchBook(jdbc, second, "Uma expedição", "B. Costa", "Viagem por uma nebulosa", false);
        insertSearchBook(jdbc, hidden, "Nebulosa", "C. Reis", null, true);

        assertThat(books.search("NEBULOSA", new PageBook(1, 0))).extracting(GetBookResponse::id)
                .containsExactly(first);
        assertThat(books.search("nebulosa", new PageBook(1, 1))).extracting(GetBookResponse::id)
                .containsExactly(second);
        assertThat(books.search("Silva", new PageBook(10, 0))).extracting(GetBookResponse::id)
                .containsExactly(first);
        assertThat(books.search("sem correspondência", new PageBook(10, 0))).isEmpty();
        assertThatThrownBy(() -> books.search("  ", new PageBook(10, 0)))
                .isInstanceOf(BookDomainException.class);
    }

    private void insertSearchBook(JdbcTemplate jdbc, UUID id, String title, String author,
                                  String description, boolean deleted) {
        jdbc.update("""
                INSERT INTO books(id, title, author, description, release_year, isbn, page_count,
                                  publisher, version, created_at, deleted_at)
                VALUES (?, ?, ?, ?, 2025, ?, 150, 'Fixture', 0, now(),
                        CASE WHEN ? THEN now() ELSE NULL END)
                """, id, title, author, description, id.toString(), deleted);
    }

    @Test
    @Order(5)
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
