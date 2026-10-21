package com.vellumhub.catalog_service.module.outbox.infrastructure.dispatcher;

import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.catalog_service.module.outbox.infrastructure.persistence.JdbcOutboxStore;
import com.vellumhub.catalog_service.module.outbox.infrastructure.persistence.JdbcOutboxWriter;
import com.vellumhub.catalog_service.module.outbox.infrastructure.kafka.KafkaOutboxTransport;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import com.vellumhub.testing.distributed.container.KafkaIntegrationTestSupport;
import com.vellumhub.testing.distributed.kafka.KafkaProbe;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Tag("distributed")
class BookOutboxKafkaDistributedTest extends KafkaIntegrationTestSupport {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:15-alpine");
    static JdbcTemplate jdbc;
    @BeforeAll static void setupDatabase() {
        var ds=new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);
    }

    @Test void publishesBookSnapshotWithStableEventIdAndKafkaTypeHeader() {
        var props=new java.util.HashMap<String,Object>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,JsonSerializer.class);
        props.put("spring.json.add.type.headers",true);
        props.put("spring.json.type.mapping",
            "book_changed_event:com.vellumhub.kafka.contracts.book.BookChangedEvent");
        var producerFactory=new DefaultKafkaProducerFactory<String,Object>(props);
        try {
            UUID id=UUID.randomUUID(),eventId=UUID.randomUUID();
            var fact=new BookChangedEvent(eventId,Instant.now(),id,0,
                    BookChangedEvent.Kind.CREATED,
                    new BookChangedEvent.State("Dune","SF",1965,null,"Herbert","ISBN",400,"Ace",List.of("SF")));
            var store=new JdbcOutboxStore(jdbc);
            var sender=new KafkaOutboxTransport(new KafkaTemplate<>(producerFactory));
            var dispatcher=new OutboxDispatcher(store,sender,new SimpleMeterRegistry());
            try(var probe=KafkaProbe.subscribe(KAFKA.getBootstrapServers(),KafkaTopics.BOOK_EVENTS,"book-outbox")) {
                new JdbcOutboxWriter(jdbc).append(OutboxMessage.book(fact));
                dispatcher.dispatch();
                var record=probe.awaitRecord(KafkaTopics.BOOK_EVENTS,id.toString(),
                        ASYNC_TIMEOUT,ASYNC_POLL_INTERVAL);
                assertThat(new String(record.value(),StandardCharsets.UTF_8))
                        .contains(eventId.toString()).contains("\"revision\":0");
                assertThat(KafkaProbe.utf8HeaderValues(record,"__TypeId__"))
                        .contains("book_changed_event");
                await().atMost(Duration.ofSeconds(15)).untilAsserted(()->assertThat(
                    jdbc.queryForObject("SELECT status FROM outbox_events WHERE event_id=?",
                        String.class,eventId)).isEqualTo("PUBLISHED"));
            }
        } finally { producerFactory.destroy(); }
    }
    /**
     * AFF-EVT-005/006: a broker ACK followed by failure to persist PUBLISHED
     * must leave the event reclaimable. A second physical publish is acceptable
     * and retains the original event identity for consumer deduplication.
     */
    @Test void acknowledgedPublishCanBeSafelyReplayedAfterMarkFailure() throws Exception {
        var props = new java.util.HashMap<String,Object>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put("spring.json.add.type.headers", true);
        props.put("spring.json.type.mapping",
                "book_changed_event:com.vellumhub.kafka.contracts.book.BookChangedEvent");
        var producerFactory = new DefaultKafkaProducerFactory<String,Object>(props);
        try {
            UUID book = UUID.randomUUID(), eventId = UUID.randomUUID();
            var event = new BookChangedEvent(eventId, Instant.now(), book, 0,
                    BookChangedEvent.Kind.CREATED,
                    new BookChangedEvent.State("Dune", "SF", 1965, null,
                            "Herbert", "ISBN", 400, "Ace", List.of("SF")));
            var store = org.mockito.Mockito.spy(new JdbcOutboxStore(jdbc));
            org.mockito.Mockito.doThrow(new IllegalStateException("injected marking failure"))
                    .doCallRealMethod().when(store).published(
                            org.mockito.ArgumentMatchers.eq(eventId),
                            org.mockito.ArgumentMatchers.any(UUID.class));
            var dispatcher = new OutboxDispatcher(store,
                    new KafkaOutboxTransport(new KafkaTemplate<>(producerFactory)),
                    new SimpleMeterRegistry());
            try (var probe = KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                    KafkaTopics.BOOK_EVENTS, "ack-recovery-aff")) {
                new JdbcOutboxWriter(jdbc).append(OutboxMessage.book(event));
                dispatcher.dispatch();
                var first = probe.awaitRecord(KafkaTopics.BOOK_EVENTS, book.toString(),
                        ASYNC_TIMEOUT, ASYNC_POLL_INTERVAL);
                assertThat(new String(first.value(), StandardCharsets.UTF_8))
                        .contains(eventId.toString());
                org.mockito.Mockito.verify(store, org.mockito.Mockito.timeout(15000))
                        .published(org.mockito.ArgumentMatchers.eq(eventId),
                                org.mockito.ArgumentMatchers.any(UUID.class));
                assertThat(jdbc.queryForObject(
                        "SELECT status FROM outbox_events WHERE event_id=?", String.class,
                        eventId)).isEqualTo("IN_FLIGHT");

                jdbc.update("UPDATE outbox_events SET lease_until=now()-interval '1 second' WHERE event_id=?",
                        eventId);
                dispatcher.dispatch();
                var replay = probe.awaitRecord(KafkaTopics.BOOK_EVENTS, book.toString(),
                        ASYNC_TIMEOUT, ASYNC_POLL_INTERVAL);
                assertThat(new String(replay.value(), StandardCharsets.UTF_8))
                        .contains(eventId.toString());
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                        assertThat(jdbc.queryForObject(
                                "SELECT status FROM outbox_events WHERE event_id=?",
                                String.class, eventId)).isEqualTo("PUBLISHED"));
            }
        } finally {
            producerFactory.destroy();
        }
    }

    /**
     * AFF-EVT-005: a transport outage after the local commit must not lose the
     * durable outbox intent. Once delivery becomes available, the exact event
     * identity is published without repeating the domain transaction.
     *
     * Failure is injected at the transport boundary; recovery uses real Kafka
     * and the production transport implementation.
     */
    @Test void brokerTransportFailurePreservesCommittedOutboxUntilRecovery() {
        var props = new java.util.HashMap<String,Object>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put("spring.json.add.type.headers", true);
        props.put("spring.json.type.mapping",
                "book_changed_event:com.vellumhub.kafka.contracts.book.BookChangedEvent");
        var producerFactory = new DefaultKafkaProducerFactory<String,Object>(props);
        try {
            UUID book = UUID.randomUUID(), eventId = UUID.randomUUID();
            var fact = new BookChangedEvent(eventId, Instant.now(), book, 0,
                    BookChangedEvent.Kind.CREATED,
                    new BookChangedEvent.State("Outage book", "SF", 2026, null,
                            "Author", "ISBN", 100, "Publisher", List.of("SF")));
            var store = new JdbcOutboxStore(jdbc);
            new JdbcOutboxWriter(jdbc).append(OutboxMessage.book(fact));

            var unavailable = org.mockito.Mockito.mock(KafkaOutboxTransport.class);
            org.mockito.Mockito.when(unavailable.send(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(
                            new org.apache.kafka.common.errors.TimeoutException("broker unavailable")));
            var failedDispatcher = new OutboxDispatcher(store, unavailable, new SimpleMeterRegistry());
            failedDispatcher.dispatch();
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(jdbc.queryForObject(
                        "SELECT status FROM outbox_events WHERE event_id=?", String.class, eventId))
                        .isEqualTo("PENDING");
                assertThat(jdbc.queryForObject(
                        "SELECT attempts FROM outbox_events WHERE event_id=?", Integer.class, eventId))
                        .isGreaterThanOrEqualTo(1);
            });

            // Retry backoff is advanced explicitly, without sleeping or
            // relying on any scheduler to wake up.
            jdbc.update("UPDATE outbox_events SET next_attempt_at=now() WHERE event_id=?", eventId);
            try (var probe = KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                    KafkaTopics.BOOK_EVENTS, "outage-recovery-aff")) {
                var healthyDispatcher = new OutboxDispatcher(store,
                        new KafkaOutboxTransport(new KafkaTemplate<>(producerFactory)),
                        new SimpleMeterRegistry());
                healthyDispatcher.dispatch();
                var delivered = probe.awaitRecord(KafkaTopics.BOOK_EVENTS, book.toString(),
                        ASYNC_TIMEOUT, ASYNC_POLL_INTERVAL);
                assertThat(new String(delivered.value(), StandardCharsets.UTF_8))
                        .contains(eventId.toString());
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                        assertThat(jdbc.queryForObject(
                                "SELECT status FROM outbox_events WHERE event_id=?",
                                String.class, eventId)).isEqualTo("PUBLISHED"));
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM outbox_events WHERE event_id=?",
                        Integer.class, eventId)).isEqualTo(1);
            }
        } finally {
            producerFactory.destroy();
        }
    }

}

