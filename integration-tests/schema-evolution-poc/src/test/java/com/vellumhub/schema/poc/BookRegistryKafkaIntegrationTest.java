package com.vellumhub.schema.poc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Isolated contract evolution spike: real Kafka + Apicurio, no application listeners.
 * Uses an ephemeral in-memory Registry; persistence and production ACLs belong to #348.
 */
@Tag("distributed")
@Testcontainers
class BookRegistryKafkaIntegrationTest {
    @Container
    static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Container
    static final GenericContainer<?> REGISTRY =
            new GenericContainer<>(DockerImageName.parse("apicurio/apicurio-registry:3.1.7"))
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)));

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void confluentCcompatEnforcesBackwardTransitiveAndReadsHistoricalBytes() throws Exception {
        Schema v1 = BookSchemaFixtures.schema("book-v1.avsc");
        Schema v2 = BookSchemaFixtures.schema("book-v2.avsc");
        Schema v3 = BookSchemaFixtures.schema("book-v3-compatible.avsc");
        Schema invalidV3 = BookSchemaFixtures.schema("book-v3-incompatible.avsc");
        String subject = BookSchemaFixtures.TOPIC + "-" + v1.getFullName();
        String subjectPath = URLEncoder.encode(subject, StandardCharsets.UTF_8);

        HttpResponse<String> config = http("PUT", "/config", "{\"compatibility\":\"BACKWARD_TRANSITIVE\"}");
        assertEquals(200, config.statusCode(), config.body());
        HttpResponse<String> effectiveConfig = http("GET", "/config", null);
        assertEquals(200, effectiveConfig.statusCode(), effectiveConfig.body());
        var effective = JSON.readTree(effectiveConfig.body());
        assertEquals("BACKWARD_TRANSITIVE",
                effective.path("compatibility").asText(effective.path("compatibilityLevel").asText()),
                "The effective Registry compatibility policy must be transitive");

        // Register v1 and v2; v3 WITHOUT a default reads v2 but cannot read v1.
        List<Integer> schemaIds = new ArrayList<>();
        for (Schema schema : List.of(v1, v2)) {
            HttpResponse<String> registered = http("POST", "/subjects/" + subjectPath + "/versions",
                    requestSchema(schema));
            assertEquals(200, registered.statusCode(), registered.body());
            int id = JSON.readTree(registered.body()).path("id").asInt(-1);
            assertTrue(id > 0, registered.body());
            schemaIds.add(id);
        }
        HttpResponse<String> broken = http("POST", "/subjects/" + subjectPath + "/versions",
                requestSchema(invalidV3));
        assertEquals(409, broken.statusCode(), broken.body());
        HttpResponse<String> safe = http("POST", "/subjects/" + subjectPath + "/versions",
                requestSchema(v3));
        assertEquals(200, safe.statusCode(), safe.body());

        HttpResponse<String> listed = http("GET", "/subjects", null);
        assertEquals(200, listed.statusCode(), listed.body());
        assertTrue(JSON.readTree(listed.body()).toString().contains("\"" + subject + "\""),
                "TopicRecordNameStrategy must use a subject per topic and Avro record: " + listed.body());
        HttpResponse<String> versions = http("GET", "/subjects/" + subjectPath + "/versions", null);
        assertEquals(200, versions.statusCode(), versions.body());
        assertEquals(3, JSON.readTree(versions.body()).size());

        Properties producer = kafkaProducer(KafkaAvroSerializer.class.getName());
        producer.put("schema.registry.url", ccompat());
        producer.put("auto.register.schemas", false);
        // Avro's String codegen adds Java-only metadata. Strip it so the generated
        // SpecificRecord resolves the Git-managed schema registered beforehand.
        producer.put("avro.remove.java.properties", true);
        producer.put("value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");

        Properties consumer = kafkaConsumer(KafkaAvroDeserializer.class.getName());
        consumer.put("schema.registry.url", ccompat());
        consumer.put("specific.avro.reader", true);

        GenericRecord oldEvent = BookSchemaFixtures.event(v1, "CREATED", 5L, true);
        var generatedV2 = BookSchemaFixtures.specificV2Event(6L);
        try (KafkaProducer<String, Object> sender = new KafkaProducer<>(producer);
             KafkaConsumer<String, Object> receiver = new KafkaConsumer<>(consumer)) {
            receiver.subscribe(List.of(BookSchemaFixtures.TOPIC));
            sender.send(new ProducerRecord<>(BookSchemaFixtures.TOPIC, BookSchemaFixtures.BOOK_ID, oldEvent))
                    .get(20, TimeUnit.SECONDS);
            ConsumerRecord<String, Object> received = awaitOne(receiver, BookSchemaFixtures.TOPIC);
            assertEquals(BookSchemaFixtures.BOOK_ID, received.key());
            assertInstanceOf(com.vellumhub.kafka.avro.book.BookChangedEvent.class, received.value(),
                    "The v2 generated specific reader must resolve a v1 writer schema");
            var upgraded = (com.vellumhub.kafka.avro.book.BookChangedEvent) received.value();
            assertNull(upgraded.getCatalogSource(), "v2 default must fill absent v1 field");
            assertEquals(5L, upgraded.getRevision());
            assertEquals("A Book", upgraded.getState().getTitle().toString());
            assertEquals(BookSchemaFixtures.EVENT_ID, upgraded.getEventId().toString());

            // The second record exercises the REAL code-generated v2 producer,
            // with schema auto-registration disabled and a pre-registered v2 ID.
            sender.send(new ProducerRecord<>(BookSchemaFixtures.TOPIC, BookSchemaFixtures.BOOK_ID, generatedV2))
                    .get(20, TimeUnit.SECONDS);
            ConsumerRecord<String, Object> v2Record = awaitOne(receiver, BookSchemaFixtures.TOPIC);
            assertEquals(BookSchemaFixtures.BOOK_ID, v2Record.key());
            assertInstanceOf(com.vellumhub.kafka.avro.book.BookChangedEvent.class, v2Record.value());
            var roundTripV2 = (com.vellumhub.kafka.avro.book.BookChangedEvent) v2Record.value();
            assertEquals(6L, roundTripV2.getRevision());
            assertEquals("A Book", roundTripV2.getState().getTitle().toString());
        }

        // Inspect actual on-wire data rather than inferring framing from
        // KafkaAvroSerializer's documented behavior.
        try (KafkaConsumer<String, byte[]> wire = new KafkaConsumer<>(
                kafkaConsumer(ByteArrayDeserializer.class.getName()))) {
            wire.subscribe(List.of(BookSchemaFixtures.TOPIC));
            List<ConsumerRecord<String, byte[]>> records =
                    awaitRecords(wire, BookSchemaFixtures.TOPIC, 2);
            assertEquals(schemaIds.get(0).intValue(), wireSchemaId(records.get(0).value()));
            assertEquals(schemaIds.get(1).intValue(), wireSchemaId(records.get(1).value()));
            assertEquals(records.get(0).partition(), records.get(1).partition(),
                    "Same bookId must preserve partition affinity");
        }
    }

    @Test
    void nativeApicurioSerdeRoundTripsAnIsolatedRecordOverKafka() throws Exception {
        // Native API: separate artifact and wire-reader; not assumed interchangeable
        // with Confluent bytes without an explicit cross-SerDe compatibility test.
        Schema v1 = BookSchemaFixtures.schema("book-v1.avsc");
        Properties producer = kafkaProducer(AvroKafkaSerializer.class.getName());
        producer.put("apicurio.registry.url", nativeRegistry());
        producer.put("apicurio.registry.auto-register", true);

        Properties consumer = kafkaConsumer(AvroKafkaDeserializer.class.getName());
        consumer.put("apicurio.registry.url", nativeRegistry());

        try (KafkaProducer<String, GenericRecord> sender = new KafkaProducer<>(producer);
             KafkaConsumer<String, Object> receiver = new KafkaConsumer<>(consumer)) {
            receiver.subscribe(List.of(BookSchemaFixtures.NATIVE_TOPIC));
            sender.send(new ProducerRecord<>(BookSchemaFixtures.NATIVE_TOPIC, BookSchemaFixtures.BOOK_ID,
                    BookSchemaFixtures.event(v1, "DELETED", 6L, false))).get(20, TimeUnit.SECONDS);
            Object value = awaitOne(receiver, BookSchemaFixtures.NATIVE_TOPIC).value();
            assertInstanceOf(GenericRecord.class, value);
            GenericRecord decoded = (GenericRecord) value;
            assertEquals(BookSchemaFixtures.BOOK_ID, decoded.get("bookId").toString());
            assertNull(decoded.get("state"));
            assertEquals(6L, decoded.get("revision"));
        }

        // The native Kafka serializer uses its own resolver strategy and an
        // artifact, which must be observed rather than assumed to match ccompat.
        String artifactId = BookSchemaFixtures.NATIVE_TOPIC + "-value";
        HttpResponse<String> artifact = nativeHttp("/groups/default/artifacts/" + artifactId);
        assertEquals(200, artifact.statusCode(), artifact.body());
        assertEquals(artifactId, JSON.readTree(artifact.body()).path("artifactId").asText());

        try (KafkaConsumer<String, byte[]> raw = new KafkaConsumer<>(
                kafkaConsumer(ByteArrayDeserializer.class.getName()))) {
            raw.subscribe(List.of(BookSchemaFixtures.NATIVE_TOPIC));
            byte[] bytes = awaitOne(raw, BookSchemaFixtures.NATIVE_TOPIC).value();
            assertTrue(wireSchemaId(bytes) > 0, "Apicurio 3.x default encodes a 4-byte contentId");
        }
    }

    private static <V> ConsumerRecord<String, V> awaitOne(KafkaConsumer<String, V> receiver,
                                                           String topic) {
        return awaitRecords(receiver, topic, 1).getFirst();
    }

    private static <V> List<ConsumerRecord<String, V>> awaitRecords(KafkaConsumer<String, V> receiver,
                                                                    String topic, int expected) {
        List<ConsumerRecord<String, V>> found = new ArrayList<>();
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline && found.size() < expected) {
            for (ConsumerRecord<String, V> record : receiver.poll(Duration.ofMillis(500))) {
                if (record.topic().equals(topic)) found.add(record);
            }
        }
        assertEquals(expected, found.size(),
                "Kafka did not deliver the expected schema-poc records for topic " + topic);
        return found;
    }

    private static int wireSchemaId(byte[] bytes) {
        assertTrue(bytes.length > 5, "Avro wire data must include framing and body");
        assertEquals(0, bytes[0], "Expected Avro magic byte");
        return ByteBuffer.wrap(bytes, 1, Integer.BYTES).getInt();
    }

    private static Properties kafkaProducer(String valueSerializer) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return props;
    }

    private static Properties kafkaConsumer(String valueDeserializer) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "schema-poc-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer);
        return props;
    }

    private static String registryBase() {
        return "http://" + REGISTRY.getHost() + ":" + REGISTRY.getMappedPort(8080);
    }
    private static String nativeRegistry() { return registryBase() + "/apis/registry/v3"; }

    private static HttpResponse<String> nativeHttp(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(nativeRegistry() + path))
                .timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
    private static String ccompat() { return registryBase() + "/apis/ccompat/v7"; }

    private static String requestSchema(Schema schema) throws Exception {
        return JSON.writeValueAsString(Map.of("schemaType", "AVRO", "schema", schema.toString()));
    }

    private static HttpResponse<String> http(String method, String path, String payload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ccompat() + path))
                .header("Content-Type", "application/vnd.schemaregistry.v1+json")
                .timeout(Duration.ofSeconds(20))
                .method(method, payload == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(payload))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
