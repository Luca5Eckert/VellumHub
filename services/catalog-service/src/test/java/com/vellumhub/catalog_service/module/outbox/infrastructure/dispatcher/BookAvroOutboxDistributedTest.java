package com.vellumhub.catalog_service.module.outbox.infrastructure.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.catalog_service.module.outbox.infrastructure.kafka.BookAvroPublisher;
import com.vellumhub.catalog_service.module.outbox.infrastructure.kafka.KafkaOutboxTransport;
import com.vellumhub.catalog_service.module.outbox.infrastructure.persistence.JdbcOutboxStore;
import com.vellumhub.catalog_service.module.outbox.infrastructure.persistence.JdbcOutboxWriter;
import com.vellumhub.kafka.avro.BookAvroDecoder;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import com.vellumhub.testing.distributed.container.KafkaIntegrationTestSupport;
import com.vellumhub.testing.distributed.kafka.KafkaProbe;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Real Catalog Outbox -> Kafka Confluent Avro -> Apicurio lookup, no mocked relay. */
@Tag("distributed")
class BookAvroOutboxDistributedTest extends KafkaIntegrationTestSupport {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static final GenericContainer<?> REGISTRY =
            new GenericContainer<>(DockerImageName.parse("apicurio/apicurio-registry:3.1.7"))
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(3)));

    private static final ObjectMapper JSON = new ObjectMapper();
    private static JdbcTemplate jdbc;
    @BeforeAll static void setup() {
        var ds = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds);
    }

    @Test void actualCatalogRelayPublishesSchemaIdAndOriginalBookIdentityWithoutAutoRegister()
            throws Exception {
        UUID book = UUID.randomUUID(), id = UUID.randomUUID();
        String registryUrl = "http://" + REGISTRY.getHost() + ":" + REGISTRY.getMappedPort(8080)
                + "/apis/ccompat/v7";
        int schemaId = preRegister(registryUrl);

        BookChangedEvent event = new BookChangedEvent(id, Instant.parse("2026-11-06T12:00:00Z"),
                book, 0, BookChangedEvent.Kind.CREATED,
                new BookChangedEvent.State("Dune", null, 1965, null, "Herbert", null, 412,
                        null, List.of("SF")));
        var config = new java.util.HashMap<String, Object>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put("spring.json.add.type.headers", true);
        config.put("spring.json.type.mapping",
                "book_changed_event:com.vellumhub.kafka.contracts.book.BookChangedEvent");
        var factory = new DefaultKafkaProducerFactory<String, Object>(config);
        var avro = new BookAvroPublisher(factory, registryUrl);
        try {
            new JdbcOutboxWriter(jdbc).append(OutboxMessage.book(event, true));
            var sender = new KafkaOutboxTransport(new KafkaTemplate<>(factory), avro);
            var dispatcher = new OutboxDispatcher(
                    new JdbcOutboxStore(jdbc), sender, new SimpleMeterRegistry());
            try (var probe = KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                    KafkaTopics.BOOK_EVENTS_AVRO, "catalog-avro-probe")) {
                dispatcher.dispatch();
                var actual = probe.awaitRecord(KafkaTopics.BOOK_EVENTS_AVRO,
                        book.toString(), ASYNC_TIMEOUT, ASYNC_POLL_INTERVAL);
                assertThat(actual.value()).hasSizeGreaterThan(5);
                assertThat(actual.value()[0]).isZero();
                assertThat(ByteBuffer.wrap(actual.value(), 1, 4).getInt()).isEqualTo(schemaId);
                try (BookAvroDecoder decoder = new BookAvroDecoder(registryUrl)) {
                    assertThat(decoder.decode(actual.topic(), actual.key(), actual.value()))
                            .isEqualTo(event);
                }
                await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                    assertThat(jdbc.queryForObject(
                            "SELECT status FROM outbox_events WHERE event_id=?", String.class, id))
                            .isEqualTo("PUBLISHED");
                    assertThat(jdbc.queryForObject(
                            "SELECT topic FROM outbox_events WHERE event_id=?", String.class, id))
                            .isEqualTo(KafkaTopics.BOOK_EVENTS_AVRO);
                });
            }
        } finally {
            avro.close();
            factory.destroy();
        }
    }

    private static int preRegister(String base) throws Exception {
        try (InputStream stream = com.vellumhub.kafka.avro.book.BookChangedEvent.class
                .getResourceAsStream("/BookChangedEvent.avsc")) {
            assertThat(stream).isNotNull();
            String schema = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            String subject = KafkaTopics.BOOK_EVENTS_AVRO + "-" +
                    com.vellumhub.kafka.avro.book.BookChangedEvent.getClassSchema().getFullName();
            String path = URLEncoder.encode(subject, StandardCharsets.UTF_8);
            var client = HttpClient.newHttpClient();
            var config = HttpRequest.newBuilder(URI.create(base + "/config/" + path))
                    .header("Content-Type", "application/vnd.schemaregistry.v1+json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"compatibility\":\"BACKWARD_TRANSITIVE\"}"))
                    .build();
            assertThat(client.send(config, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            String payload = JSON.writeValueAsString(Map.of("schemaType", "AVRO", "schema", schema));
            var request = HttpRequest.newBuilder(URI.create(base + "/subjects/" + path + "/versions"))
                    .header("Content-Type", "application/vnd.schemaregistry.v1+json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            return JSON.readTree(response.body()).path("id").asInt(-1);
        }
    }
}
