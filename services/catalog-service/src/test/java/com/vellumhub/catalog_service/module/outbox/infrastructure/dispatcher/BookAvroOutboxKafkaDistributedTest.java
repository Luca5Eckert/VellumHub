package com.vellumhub.catalog_service.module.outbox.infrastructure.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.catalog_service.module.outbox.infrastructure.kafka.KafkaOutboxTransport;
import com.vellumhub.catalog_service.module.outbox.infrastructure.persistence.JdbcOutboxStore;
import com.vellumhub.catalog_service.module.outbox.infrastructure.persistence.JdbcOutboxWriter;
import com.vellumhub.kafka.avro.BookAvroMapper;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import com.vellumhub.testing.distributed.container.KafkaIntegrationTestSupport;
import com.vellumhub.testing.distributed.kafka.KafkaProbe;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Catalog's real Outbox + producer -> Kafka Avro bytes and immutable Registry ID. */
@Tag("distributed")
@Testcontainers
class BookAvroOutboxKafkaDistributedTest extends KafkaIntegrationTestSupport {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static final GenericContainer<?> REGISTRY =
            new GenericContainer<>(DockerImageName.parse("apicurio/apicurio-registry:3.1.7"))
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects")
                            .forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)));
    private static final ObjectMapper JSON = new ObjectMapper();
    private static JdbcTemplate jdbc;

    @BeforeAll static void prepareDatabase() {
        var ds = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds);
    }
    private static String registryUrl() {
        return "http://" + REGISTRY.getHost() + ":" + REGISTRY.getMappedPort(8080) + "/apis/ccompat/v7";
    }
    private static int registerBookSchema() throws Exception {
        String subject = URLEncoder.encode(KafkaTopics.BOOK_EVENTS_AVRO + "-" +
                com.vellumhub.kafka.avro.book.BookChangedEvent.getClassSchema().getFullName(),
                StandardCharsets.UTF_8);
        String schema;
        try (var stream = com.vellumhub.kafka.avro.BookAvroMapper.class
                .getResourceAsStream("/BookChangedEvent.avsc")) {
            if (stream == null) throw new IllegalStateException("Canonical Book v2 schema missing");
            schema = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        var http = HttpClient.newHttpClient();
        var api = registryUrl();
        var config = http.send(HttpRequest.newBuilder(URI.create(api + "/config/" + subject))
                        .header("Content-Type", "application/vnd.schemaregistry.v1+json")
                        .PUT(HttpRequest.BodyPublishers.ofString("{\"compatibility\":\"BACKWARD_TRANSITIVE\"}"))
                        .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(config.statusCode()).isEqualTo(200);
        var payload = JSON.writeValueAsString(Map.of("schema", schema, "schemaType", "AVRO"));
        var response = http.send(HttpRequest.newBuilder(URI.create(api + "/subjects/" + subject + "/versions"))
                        .header("Content-Type", "application/vnd.schemaregistry.v1+json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("Registry must pre-register Book v2").isEqualTo(200);
        return JSON.readTree(response.body()).get("id").asInt();
    }

    @Test void realOutboxPublishesGeneratedAvroWithStableSchemaIdAndOriginalEventId() throws Exception {
        int id = registerBookSchema();
        var props = new java.util.HashMap<String,Object>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put("schema.registry.url", registryUrl());
        props.put("value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
        props.put("auto.register.schemas", false);
        props.put("avro.remove.java.properties", true);
        var factory = new DefaultKafkaProducerFactory<String,Object>(props);
        try {
            UUID bookId = UUID.randomUUID(), eventId = UUID.randomUUID();
            var event = new BookChangedEvent(eventId, Instant.parse("2026-11-07T12:00:00Z"),
                    bookId, 0L, BookChangedEvent.Kind.CREATED,
                    new BookChangedEvent.State("Dune", null, 1965, null, "Herbert",
                            null, 412, "Publisher", List.of("SF")));
            var sender = new KafkaOutboxTransport(null, new KafkaTemplate<>(factory));
            var store = new JdbcOutboxStore(jdbc);
            var dispatcher = new OutboxDispatcher(store, sender, new SimpleMeterRegistry());
            try (var probe = KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                    KafkaTopics.BOOK_EVENTS_AVRO, "catalog-book-avro")) {
                new JdbcOutboxWriter(jdbc).append(OutboxMessage.bookAvro(event));
                dispatcher.dispatch();
                var record = probe.awaitRecord(KafkaTopics.BOOK_EVENTS_AVRO, bookId.toString(),
                        ASYNC_TIMEOUT, ASYNC_POLL_INTERVAL);
                assertThat(record.value()[0]).isEqualTo((byte) 0);
                assertThat(ByteBuffer.wrap(record.value(), 1, 4).getInt()).isEqualTo(id);
                assertThat(new String(record.headers().lastHeader("x-vellum-event-id").value(),
                        StandardCharsets.UTF_8)).isEqualTo(eventId.toString());
                var deserializer = new KafkaAvroDeserializer();
                deserializer.configure(Map.of("schema.registry.url", registryUrl(),
                        "specific.avro.reader", true), false);
                var wire = (com.vellumhub.kafka.avro.book.BookChangedEvent)
                        deserializer.deserialize(record.topic(), record.value());
                assertThat(BookAvroMapper.decodeForKey(wire, record.key())).isEqualTo(event);
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                        assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE event_id=?",
                                String.class, eventId)).isEqualTo("PUBLISHED"));
                deserializer.close();
            }
        } finally { factory.destroy(); }
    }
}
