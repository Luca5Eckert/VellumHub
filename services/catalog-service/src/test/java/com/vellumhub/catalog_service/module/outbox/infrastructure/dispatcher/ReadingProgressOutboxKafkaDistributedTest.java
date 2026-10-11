package com.vellumhub.catalog_service.module.outbox.infrastructure.dispatcher;

import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.catalog_service.module.outbox.infrastructure.kafka.KafkaOutboxTransport;
import com.vellumhub.catalog_service.module.outbox.infrastructure.persistence.JdbcOutboxStore;
import com.vellumhub.catalog_service.module.outbox.infrastructure.persistence.JdbcOutboxWriter;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("distributed")
class ReadingProgressOutboxKafkaDistributedTest extends KafkaIntegrationTestSupport {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:15-alpine");
    static JdbcTemplate jdbc;

    @BeforeAll
    static void preparePostgres() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds);
    }

    @Test
    void committedOutboxEventReachesKafkaWithOriginalIdKeyAndTypeAlias() {
        Map<String,Object> settings = new HashMap<>();
        settings.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        settings.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        settings.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        settings.put("spring.json.add.type.headers", true);
        settings.put("spring.json.type.mapping",
                "reading_progress_changed_event:com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent");
        var factory = new DefaultKafkaProducerFactory<String,Object>(settings);
        try {
            UUID aggregateId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            var event = new ReadingProgressChangedEvent(
                    eventId, Instant.now(), aggregateId, UUID.randomUUID(), UUID.randomUUID(),
                    "READING", 0, 12, 0L, ReadingProgressChangedEvent.Kind.CREATED);
            var writer = new JdbcOutboxWriter(jdbc);
            var store = new JdbcOutboxStore(jdbc);
            var producer = new KafkaOutboxTransport(new KafkaTemplate<>(factory));
            var dispatcher = new OutboxDispatcher(store, producer, new SimpleMeterRegistry());

            try (KafkaProbe probe = KafkaProbe.subscribe(
                    KAFKA.getBootstrapServers(), KafkaTopics.READING_PROGRESS, "reading-outbox")) {
                writer.append(OutboxMessage.readingProgress(event));
                assertThat(jdbc.queryForObject(
                        "SELECT status FROM outbox_events WHERE event_id=?", String.class, eventId))
                        .isEqualTo("PENDING");
                dispatcher.dispatch();

                var record = probe.awaitRecord(
                        KafkaTopics.READING_PROGRESS, aggregateId.toString(),
                        ASYNC_TIMEOUT, ASYNC_POLL_INTERVAL);
                assertThat(record.key()).isEqualTo(aggregateId.toString());
                assertThat(new String(record.value(), StandardCharsets.UTF_8))
                        .contains(eventId.toString()).contains("\"revision\":0");
                assertThat(KafkaProbe.utf8HeaderValues(record, "__TypeId__"))
                        .contains("reading_progress_changed_event");
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                        assertThat(jdbc.queryForObject(
                                "SELECT status FROM outbox_events WHERE event_id=?",
                                String.class, eventId)).isEqualTo("PUBLISHED"));
            }
        } finally {
            factory.destroy();
        }
    }
}
