package com.vellumhub.testing.distributed.avro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.kafka.avro.BookAvroMapper;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.testcontainers.containers.GenericContainer;

/** Isolated test data only. Registry is explicitly pre-registered: application auto-register stays false. */
public final class BookAvroFixture {
    private static final ObjectMapper JSON = new ObjectMapper();
    private BookAvroFixture() {}

    public static String registryUrl(GenericContainer<?> registry) {
        return "http://" + registry.getHost() + ":" + registry.getMappedPort(8080)
                + "/apis/ccompat/v7";
    }

    public static int register(String base) throws Exception {
        String topicSubject = KafkaTopics.BOOK_EVENTS_AVRO + "-"
                + com.vellumhub.kafka.avro.book.BookChangedEvent.getClassSchema().getFullName();
        String subject = URLEncoder.encode(topicSubject, StandardCharsets.UTF_8);
        try (var stream = com.vellumhub.kafka.avro.book.BookChangedEvent.class
                .getResourceAsStream("/BookChangedEvent.avsc")) {
            if (stream == null) throw new IllegalStateException("Missing approved Book Avro contract resource");
            // Match the production Registry gate: register the approved AVSC
            // document, not Schema.toString()'s re-rendered Java Avro variant.
            String approvedSchemaJson = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            new Schema.Parser().parse(approvedSchemaJson);
            HttpClient http = HttpClient.newHttpClient();
            var config = HttpRequest.newBuilder(URI.create(base + "/config/" + subject))
                    .header("Content-Type", "application/vnd.schemaregistry.v1+json")
                    .PUT(HttpRequest.BodyPublishers.ofString(
                            "{\"compatibility\":\"BACKWARD_TRANSITIVE\"}")).build();
            var response = http.send(config, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                throw new IllegalStateException("Cannot configure Book subject: HTTP " + response.statusCode());
            // Register the immutable historical writer before v2, so actual listeners resolve both IDs.
            try (var historical = BookAvroFixture.class.getResourceAsStream("/history/book-v1.avsc")) {
                if (historical == null) throw new IllegalStateException("Missing historical Book v1 schema");
                String historicalJson = new String(historical.readAllBytes(), StandardCharsets.UTF_8);
                new Schema.Parser().parse(historicalJson);
                publishSchema(http, base, subject, historicalJson);
            }
            var payload = JSON.writeValueAsString(Map.of("schemaType", "AVRO",
                    "schema", JSON.writeValueAsString(JSON.readTree(approvedSchemaJson))));
            var req = HttpRequest.newBuilder(URI.create(base + "/subjects/" + subject + "/versions"))
                    .header("Content-Type", "application/vnd.schemaregistry.v1+json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            var registered = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (registered.statusCode() != 200)
                throw new IllegalStateException("Cannot register Book v2: HTTP " + registered.statusCode());
            int schemaId = JSON.readTree(registered.body()).path("id").asInt(-1);
            if (schemaId <= 0) throw new IllegalStateException("Invalid schema ID");
            return schemaId;
        }
    }

    private static void publishSchema(HttpClient http, String base, String subject, String schemaJson) throws Exception {
        var req = HttpRequest.newBuilder(URI.create(base + "/subjects/" + subject + "/versions"))
                .header("Content-Type", "application/vnd.schemaregistry.v1+json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                        Map.of("schemaType", "AVRO",
                               "schema", JSON.writeValueAsString(JSON.readTree(schemaJson)))))).build();
        if (http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode() != 200)
            throw new IllegalStateException("Cannot register historical Book writer");
    }

    public static GenericData.Record encodedV1(BookChangedEvent event) throws Exception {
        try (var stream = BookAvroFixture.class.getResourceAsStream("/history/book-v1.avsc")) {
            if (stream == null) throw new IllegalStateException("Missing historical Book schema");
            var schema = new Schema.Parser().parse(stream);
            var current = encoded(event);
            var historical = new GenericData.Record(schema);
            for (var field : schema.getFields()) historical.put(field.name(), current.get(field.name()));
            return historical;
        }
    }

    public static KafkaProducer<String, Object> producer(String bootstrap, String registryUrl) {
        Properties options = new Properties();
        options.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        options.put(ProducerConfig.ACKS_CONFIG, "all");
        options.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        options.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
        options.put("schema.registry.url", registryUrl);
        options.put("auto.register.schemas", false);
        options.put("avro.remove.java.properties", true);
        options.put("value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
        return new KafkaProducer<>(options);
    }

    public static BookChangedEvent event(UUID bookId, long revision, BookChangedEvent.Kind kind) {
        var state = kind == BookChangedEvent.Kind.DELETED ? null
                : new BookChangedEvent.State("Book-" + revision, null, 2025, null, "Author", null,
                        256, null, List.of("technical"));
        return new BookChangedEvent(UUID.randomUUID(), Instant.parse("2026-11-07T12:00:00Z"),
                bookId, revision, kind, state);
    }

    public static com.vellumhub.kafka.avro.book.BookChangedEvent encoded(BookChangedEvent event) {
        return BookAvroMapper.toAvro(event);
    }
}
