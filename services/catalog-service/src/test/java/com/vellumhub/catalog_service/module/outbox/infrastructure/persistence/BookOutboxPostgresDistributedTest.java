package com.vellumhub.catalog_service.module.outbox.infrastructure.persistence;

import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@Tag("distributed")
class BookOutboxPostgresDistributedTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:15-alpine");
    static JdbcTemplate jdbc;
    static JdbcOutboxWriter writer;
    static JdbcOutboxStore store;
    static DriverManagerDataSource ds;

    @BeforeAll static void setup() {
        ds=new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);
        writer=new JdbcOutboxWriter(jdbc);
        store=new JdbcOutboxStore(jdbc);
    }

    private OutboxMessage event(UUID book,long rev,BookChangedEvent.Kind kind) {
        var state=kind==BookChangedEvent.Kind.DELETED?null:
            new BookChangedEvent.State("Dune","Desert",1965,null,"Herbert","ISBN",400,"Ace",List.of("SF"));
        return OutboxMessage.book(new BookChangedEvent(
            UUID.randomUUID(),Instant.now(),book,rev,kind,state));
    }

    @Test void bookAndProgressHaveIndependentAggregateSequenceNamespaces() {
        UUID book=UUID.randomUUID();
        writer.append(event(book,0,BookChangedEvent.Kind.CREATED));
        writer.append(event(book,1,BookChangedEvent.Kind.UPDATED));
        var first=store.claimNext(Duration.ofSeconds(30)).orElseThrow();
        assertThat(jdbc.queryForObject("SELECT aggregate_type FROM outbox_events WHERE event_id=?",
            String.class,first.eventId())).isEqualTo("BOOK");
        assertThat(store.claimNext(Duration.ofSeconds(30))).isEmpty();
        store.published(first.eventId(),first.claimToken());
        var next=store.claimNext(Duration.ofSeconds(30)).orElseThrow();
        assertThat(next.eventType()).isEqualTo("BookChangedEvent");
        store.published(next.eventId(),next.claimToken());
    }

    @Test void domainAndOutboxMustCommitTogether() {
        UUID book=UUID.randomUUID();
        var transaction=new TransactionTemplate(new DataSourceTransactionManager(ds));
        assertThatThrownBy(()->transaction.executeWithoutResult(status->{
            jdbc.update("""
                INSERT INTO books(id,title,release_year,author,isbn,page_count,publisher,version,created_at)
                VALUES(?,?,?,?,?,?,?,0,now())
                """,book,"Dune",1965,"Herbert","ISBN",400,"Ace");
            writer.append(event(book,0,BookChangedEvent.Kind.CREATED));
            throw new IllegalStateException("failure after append");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM books WHERE id=?",
            Integer.class,book)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id=?",
            Integer.class,book)).isZero();
    }
    /**
     * AFF-EVT-005: an Outbox unique-constraint failure must abort a simultaneous
     * aggregate mutation; a preexisting event remains durable.
     */
    @Test void outboxInsertFailureRollsBackBusinessMutation() {
        UUID book = UUID.randomUUID();
        OutboxMessage duplicate = event(book, 0, BookChangedEvent.Kind.CREATED);
        writer.append(duplicate);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("""
                INSERT INTO books(id,title,release_year,author,isbn,page_count,publisher,version,created_at)
                VALUES(?,?,?,?,?,?,?,0,now())
                """, book, "Dune", 1965, "Herbert", "ISBN-2", 400, "Ace");
            // Reusing the exact event ID forces a database uniqueness failure.
            writer.append(duplicate);
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM books WHERE id=?",
                Integer.class, book)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_id=?",
                Integer.class, duplicate.eventId())).isEqualTo(1);
    }

    /** AFF-EVT-005: BookProgress Outbox insertion failure cannot commit domain state. */
    @Test void readingProgressOutboxConstraintFailureRollsBackDomainMutation() {
        UUID progress = UUID.randomUUID();
        var event = new com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent(
                UUID.randomUUID(), Instant.now(), progress, UUID.randomUUID(), UUID.randomUUID(),
                "READING", 0, 10, 0,
                com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent.Kind.CREATED);
        var duplicate = OutboxMessage.readingProgress(event);
        writer.append(duplicate);
        jdbc.execute("CREATE TABLE IF NOT EXISTS aff_progress_mutations (id uuid PRIMARY KEY)");
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            // Use a test-scoped table so this invariant does not rely on a
            // particular historical BookProgress aggregate migration.
            jdbc.update("INSERT INTO aff_progress_mutations(id) VALUES(?)", progress);
            writer.append(duplicate);
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM aff_progress_mutations WHERE id=?", Integer.class, progress))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_id=?", Integer.class, event.eventId()))
                .isEqualTo(1);
    }

    @Test void legacyJsonRowWithoutNewColumnsRemainsEligibleAfterFlyway() throws Exception {
        UUID book = UUID.randomUUID();
        var message = event(book, 0, BookChangedEvent.Kind.CREATED);
        var json = com.fasterxml.jackson.databind.json.JsonMapper.builder().findAndAddModules()
                .build().writeValueAsString(message.payload());
        jdbc.update("""
                INSERT INTO outbox_events(event_id, aggregate_type, aggregate_id,
                     aggregate_version, event_type, topic, message_key, payload)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """, message.eventId(), "BOOK", book, 0L, "BookChangedEvent",
                "book-events", book.toString(), json);
        var state = jdbc.queryForMap("SELECT wire_format, payload_schema_version, payload::text AS body FROM outbox_events WHERE event_id=?",
                message.eventId());
        assertThat(state.get("wire_format")).isEqualTo("JSON");
        assertThat(state.get("payload_schema_version")).isEqualTo(1);
        assertThat((String) state.get("body")).contains(message.eventId().toString());
    }

    @Test void newAvroBookOutboxRowRetainsJsonbAndImmutableDestination() {
        UUID book = UUID.randomUUID();
        var message = event(book, 0, BookChangedEvent.Kind.CREATED);
        var avro = OutboxMessage.book((BookChangedEvent) message.payload(), true);
        writer.append(avro);
        var state = jdbc.queryForMap("""
                SELECT topic, wire_format, payload_schema_version, aggregate_version,
                       payload->>'eventId' AS stored_event_id
                FROM outbox_events WHERE event_id=?
                """, message.eventId());
        assertThat(state.get("topic")).isEqualTo("book-events.avro");
        assertThat(state.get("wire_format")).isEqualTo("AVRO");
        assertThat(state.get("payload_schema_version")).isEqualTo(1);
        assertThat(((Number) state.get("aggregate_version")).longValue()).isZero();
        assertThat(state.get("stored_event_id")).isEqualTo(message.eventId().toString());
    }

    @Test void BookFreezeFenceRejectsMutationsAndSupportsSafeReopening() {
        var gate = new com.vellumhub.catalog_service.module.book.infrastructure.persistence.JdbcBookWriteGate(jdbc);
        var txn = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.update("UPDATE book_write_gate SET frozen=true, updated_at=now() WHERE id=1");
        try {
            assertThatThrownBy(() -> txn.executeWithoutResult(status -> gate.requireWritable()))
                .isInstanceOf(com.vellumhub.catalog_service.module.book.domain.exception.BookWritesFrozenException.class);
            assertThat(jdbc.queryForObject("SELECT frozen FROM book_write_gate WHERE id=1", Boolean.class))
                .isTrue();
        } finally {
            jdbc.update("UPDATE book_write_gate SET frozen=false, updated_at=now() WHERE id=1");
        }
        assertThatCode(() -> txn.executeWithoutResult(status -> gate.requireWritable()))
            .doesNotThrowAnyException();
        assertThatThrownBy(gate::requireWritable)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("same database transaction");
    }

    @Test void FreezeWaitsForInFlightBookOutboxTransaction() throws Exception {
        var gate = new com.vellumhub.catalog_service.module.book.infrastructure.persistence.JdbcBookWriteGate(jdbc);
        var txn = new TransactionTemplate(new DataSourceTransactionManager(ds));
        var acquired = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var writer = workers.submit(() -> txn.executeWithoutResult(status -> {
                gate.requireWritable();
                acquired.countDown();
                try {
                    if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                        throw new IllegalStateException("Timed out holding Book gate");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }));
            assertThat(acquired.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var freezer = workers.submit(() -> jdbc.update(
                    "UPDATE book_write_gate SET frozen=true, updated_at=now() WHERE id=1"));
            try {
                assertThatThrownBy(() -> freezer.get(200, java.util.concurrent.TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally {
                release.countDown();
            }
            writer.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(freezer.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            release.countDown();
            jdbc.update("UPDATE book_write_gate SET frozen=false, updated_at=now() WHERE id=1");
        }
    }

}

