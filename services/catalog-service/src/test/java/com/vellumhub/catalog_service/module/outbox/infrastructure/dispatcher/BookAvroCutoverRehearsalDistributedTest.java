package com.vellumhub.catalog_service.module.outbox.infrastructure.dispatcher;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vellumhub.catalog_service.module.book.infrastructure.persistence.JdbcBookWriteGate;
import com.vellumhub.catalog_service.module.book.domain.exception.BookWritesFrozenException;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.catalog_service.module.outbox.infrastructure.kafka.BookAvroPublisher;
import com.vellumhub.catalog_service.module.outbox.infrastructure.kafka.KafkaOutboxTransport;
import com.vellumhub.catalog_service.module.outbox.infrastructure.persistence.JdbcOutboxStore;
import com.vellumhub.catalog_service.module.outbox.infrastructure.persistence.JdbcOutboxWriter;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import com.vellumhub.testing.distributed.avro.BookAvroFixture;
import com.vellumhub.testing.distributed.container.KafkaIntegrationTestSupport;
import com.vellumhub.testing.distributed.kafka.KafkaProbe;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Migration rehearsal: actual pre-upgrade JSONB + SQL fence + Kafka JSON/Avro/JSON rollback. */
@Tag("distributed")
@Tag("book-avro-e2e")
class BookAvroCutoverRehearsalDistributedTest extends KafkaIntegrationTestSupport {
    @Container static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static final GenericContainer<?> REGISTRY =
            new GenericContainer<>(DockerImageName.parse("apicurio/apicurio-registry:3.1.7"))
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200));

    @Test
    void frozenWritersDrainHistoricalJsonThenUseAvroAndRollBackWithoutRewritingPendingRows()
            throws Exception {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration")
                .target("3").load().migrate();
        UUID book = UUID.randomUUID();
        BookChangedEvent beforeUpgrade = BookAvroFixture.event(book, 0, BookChangedEvent.Kind.CREATED);
        String oldJson = JsonMapper.builder().findAndAddModules().build()
                .writeValueAsString(beforeUpgrade);
        // A legacy binary inserts without wire_format or payload_schema_version columns.
        jdbc.update("""
                INSERT INTO outbox_events(event_id, aggregate_type, aggregate_id, aggregate_version,
                       event_type, topic, message_key, payload)
                VALUES (?, 'BOOK', ?, 0, 'BookChangedEvent', ?, ?, CAST(? AS jsonb))
                """, beforeUpgrade.eventId(), book, KafkaTopics.BOOK_EVENTS,
                book.toString(), oldJson);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        assertThat(jdbc.queryForObject("""
                SELECT wire_format FROM outbox_events WHERE event_id=?
                """, String.class, beforeUpgrade.eventId())).isEqualTo("JSON");

        var gate = new JdbcBookWriteGate(jdbc);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        var store = new JdbcOutboxStore(jdbc);
        var writer = new JdbcOutboxWriter(jdbc);
        var cfg = new HashMap<String, Object>();
        cfg.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        cfg.put("spring.json.add.type.headers", true);
        cfg.put("spring.json.type.mapping",
                "book_changed_event:com.vellumhub.kafka.contracts.book.BookChangedEvent");
        String base = BookAvroFixture.registryUrl(REGISTRY);
        int schemaId = BookAvroFixture.register(base);
        var jsonFactory = new DefaultKafkaProducerFactory<String, Object>(cfg);
        var avro = new BookAvroPublisher(jsonFactory, base);
        var dispatcher = new OutboxDispatcher(store,
                new KafkaOutboxTransport(new KafkaTemplate<>(jsonFactory), avro),
                new SimpleMeterRegistry());
        try (var oldWire = KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                KafkaTopics.BOOK_EVENTS, "book-json-cutover-probe");
             var newWire = KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                     KafkaTopics.BOOK_EVENTS_AVRO, "book-avro-cutover-probe")) {

            freeze(jdbc, true);
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> gate.requireWritable()))
                    .isInstanceOf(BookWritesFrozenException.class);
            // Relay drains while writes are fenced, even when the row predates V4.
            dispatcher.dispatch();
            awaitPublished(jdbc, beforeUpgrade.eventId());
            var legacy = oldWire.awaitRecord(KafkaTopics.BOOK_EVENTS,
                    book.toString(), Duration.ofSeconds(20), Duration.ofMillis(100));
            assertThat(legacy.value()[0]).isEqualTo((byte) '{');
            assertThat(legacy.key()).isEqualTo(book.toString());

            // Book Avro cutover is permitted only after the legacy rows are drained.
            assertThat(pendingBook(jdbc)).isZero();
            freeze(jdbc, false);
            var avroChanged = BookAvroFixture.event(book, 1, BookChangedEvent.Kind.UPDATED);
            tx.executeWithoutResult(status -> {
                gate.requireWritable();
                writer.append(OutboxMessage.book(avroChanged, true));
            });
            assertThat(jdbc.queryForObject(
                    "SELECT wire_format FROM outbox_events WHERE event_id=?",
                    String.class, avroChanged.eventId())).isEqualTo("AVRO");
            freeze(jdbc, true);
            dispatcher.dispatch();
            awaitPublished(jdbc, avroChanged.eventId());
            var encoded = newWire.awaitRecord(KafkaTopics.BOOK_EVENTS_AVRO,
                    book.toString(), Duration.ofSeconds(20), Duration.ofMillis(100));
            assertThat(encoded.value()[0]).isZero();
            assertThat(java.nio.ByteBuffer.wrap(encoded.value(), 1, 4).getInt()).isEqualTo(schemaId);
            assertThat(pendingBook(jdbc)).isZero();

            // Rollback creates only new JSON rows. Existing Avro rows retain destination.
            freeze(jdbc, false);
            var rollback = BookAvroFixture.event(book, 2, BookChangedEvent.Kind.UPDATED);
            tx.executeWithoutResult(status -> {
                gate.requireWritable();
                writer.append(OutboxMessage.book(rollback, false));
            });
            freeze(jdbc, true);
            dispatcher.dispatch();
            awaitPublished(jdbc, rollback.eventId());
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM outbox_events
                    WHERE aggregate_type='BOOK' AND status='PUBLISHED'
                    """, Long.class)).isEqualTo(3L);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM outbox_events
                    WHERE aggregate_type='BOOK' AND wire_format='AVRO'
                      AND topic=? AND status='PUBLISHED'
                    """, Long.class, KafkaTopics.BOOK_EVENTS_AVRO)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM outbox_events
                    WHERE aggregate_type='BOOK' AND wire_format='JSON'
                      AND topic=? AND status='PUBLISHED'
                    """, Long.class, KafkaTopics.BOOK_EVENTS)).isEqualTo(2L);
            // No Avro was ever published to the JSON topic.
            assertThat(oldWire.awaitRecord(KafkaTopics.BOOK_EVENTS,
                    book.toString(), Duration.ofSeconds(10),
                    Duration.ofMillis(100)).value()[0]).isNotZero();
        } finally {
            freeze(jdbc, false);
            avro.close();
            jsonFactory.destroy();
        }
    }

    private static long pendingBook(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_events
                WHERE aggregate_type='BOOK' AND status <> 'PUBLISHED'
                """, Long.class);
    }
    private static void awaitPublished(JdbcTemplate jdbc, UUID eventId) {
        await().atMost(Duration.ofSeconds(25)).untilAsserted(() ->
                assertThat(jdbc.queryForObject(
                        "SELECT status FROM outbox_events WHERE event_id=?",
                        String.class, eventId)).isEqualTo("PUBLISHED"));
    }
    private static void freeze(JdbcTemplate jdbc, boolean frozen) {
        jdbc.update("UPDATE book_write_gate SET frozen=?, updated_at=now() WHERE id=1", frozen);
    }
}
