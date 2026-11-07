package com.vellumhub.catalog_service.module.outbox.infrastructure.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

/** Proves upgrade of an actual pre-Flyway-4 row, not a row inserted with the new schema. */
@Tag("distributed")
class BookAvroOutboxUpgradeDistributedTest {
    @Test
    void existingPendingJsonRowKeepsEventIdentityRevisionAndTopicAfterUpgrade() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:15-alpine")) {
            db.start();
            var ds = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
            Flyway.configure().dataSource(ds).locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("3")).load().migrate();
            var jdbc = new JdbcTemplate(ds);
            UUID bookId = UUID.randomUUID(), eventId = UUID.randomUUID();
            var event = new BookChangedEvent(eventId, Instant.parse("2026-11-07T12:00:00Z"),
                    bookId, 7L, BookChangedEvent.Kind.UPDATED,
                    new BookChangedEvent.State("Book", null, 2024, null,
                            "Author", null, 100, null, List.of("fiction")));
            var json = JsonMapper.builder().findAndAddModules().build().writeValueAsString(event);
            jdbc.update("""
                INSERT INTO outbox_events(event_id, aggregate_type, aggregate_id, aggregate_version,
                                          event_type, topic, message_key, payload)
                VALUES (?, 'BOOK', ?, 7, 'BookChangedEvent', 'book-events', ?, CAST(? AS jsonb))
                """, eventId, bookId, bookId.toString(), json);
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
            var store = new JdbcOutboxStore(jdbc);
            var recovered = store.claimNext(Duration.ofSeconds(30)).orElseThrow();
            assertThat(recovered.eventId()).isEqualTo(eventId);
            assertThat(recovered.messageKey()).isEqualTo(bookId.toString());
            assertThat(recovered.wireFormat()).isEqualTo("JSON");
            assertThat(recovered.payloadSchemaVersion()).isEqualTo(1);
            assertThat(recovered.topic()).isEqualTo(KafkaTopics.BOOK_EVENTS);
            var persistedJson = JsonMapper.builder().findAndAddModules().build()
                    .readTree(recovered.payload());
            assertThat(persistedJson.path("eventId").asText()).isEqualTo(eventId.toString());
            assertThat(persistedJson.path("revision").asLong()).isEqualTo(7L);
            store.published(recovered.eventId(), recovered.claimToken());
            assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE event_id=?",
                    String.class, eventId)).isEqualTo("PUBLISHED");

            UUID nextEvent = UUID.randomUUID(), anotherBook = UUID.randomUUID();
            var next = new BookChangedEvent(nextEvent, Instant.now(), anotherBook, 0L,
                    BookChangedEvent.Kind.CREATED, event.state());
            new JdbcOutboxWriter(jdbc).append(OutboxMessage.bookAvro(next));
            assertThat(jdbc.queryForObject("SELECT wire_format FROM outbox_events WHERE event_id=?",
                    String.class, nextEvent)).isEqualTo("AVRO");
            assertThat(jdbc.queryForObject("SELECT topic FROM outbox_events WHERE event_id=?",
                    String.class, nextEvent)).isEqualTo(KafkaTopics.BOOK_EVENTS_AVRO);
            assertThat(store.claimNext(Duration.ofSeconds(30)).orElseThrow().wireFormat())
                    .isEqualTo("AVRO");
        }
    }
}
