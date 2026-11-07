package com.vellumhub.schema.poc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.avro.Schema;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;

/** Real SQL backend, real Kafka and a cold serializer/consumer after Registry restart. */
@Tag("distributed")
class PersistentRegistryIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    @Test
    void registryRestartPreservesIdsAndFailedLookupCannotPublish() throws Exception {
        try (Network network = Network.newNetwork();
             GenericContainer<?> postgres = new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
                     .withNetwork(network).withNetworkAliases("schema-db").withExposedPorts(5432)
                     .withEnv("POSTGRES_DB", "registrydb")
                     .withEnv("POSTGRES_USER", "registry_user")
                     .withEnv("POSTGRES_PASSWORD", "integration-fixture-only")
                     .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));
             ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
                     DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))) {
            postgres.start();
            kafka.start();
            GenericContainer<?> registry = registry(network);
            try {
                registry.start();
                Schema v1 = BookSchemaFixtures.schema("book-v1.avsc");
                Schema v2 = BookSchemaFixtures.schema("book-v2.avsc");
                String topic = "book-events.schema-persistent";
                String subject = URLEncoder.encode(topic + "-" + v1.getFullName(), StandardCharsets.UTF_8);
                String endpoint = url(registry);
                assertEquals(200, api(endpoint, "PUT", "/config/" + subject,
                        "{\"compatibility\":\"BACKWARD_TRANSITIVE\"}").statusCode());
                int id1 = register(endpoint, subject, v1);
                int id2 = register(endpoint, subject, v2);
                assertTrue(id1 > 0 && id2 > 0);
                try (KafkaProducer<String, Object> producer = producer(kafka.getBootstrapServers(), endpoint)) {
                    producer.send(new ProducerRecord<>(topic, BookSchemaFixtures.BOOK_ID,
                            BookSchemaFixtures.specificV2Event(8L))).get(20, TimeUnit.SECONDS);
                }
                registry.stop();
                // A new producer has an empty schema-ID cache. The Registry is offline:
                // schema lookup must fail before any invalid Kafka bytes are emitted.
                try (KafkaProducer<String, Object> fresh = producer(kafka.getBootstrapServers(), endpoint)) {
                    assertThrows(SerializationException.class, () -> fresh.send(new ProducerRecord<>(
                            topic, BookSchemaFixtures.BOOK_ID,
                            BookSchemaFixtures.specificV2Event(9L))).get(20, TimeUnit.SECONDS));
                }
                registry = registry(network);
                registry.start();
                String restored = url(registry);
                assertEquals(id1, versionId(restored, subject, 1));
                assertEquals(id2, versionId(restored, subject, 2));
                assertEquals(200, api(restored, "GET", "/schemas/ids/" + id1, null).statusCode());
                assertEquals(200, api(restored, "GET", "/schemas/ids/" + id2, null).statusCode());

                Properties cfg = new Properties();
                cfg.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
                cfg.put(ConsumerConfig.GROUP_ID_CONFIG, "registry-cold-" + UUID.randomUUID());
                cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
                cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
                cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
                cfg.put("schema.registry.url", restored);
                cfg.put("specific.avro.reader", true);
                try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(cfg)) {
                    consumer.subscribe(List.of(topic));
                    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
                    int received = 0;
                    while (System.nanoTime() < deadline) {
                        for (var record : consumer.poll(Duration.ofMillis(300))) {
                            assertEquals(BookSchemaFixtures.BOOK_ID, record.key());
                            assertInstanceOf(com.vellumhub.kafka.avro.book.BookChangedEvent.class, record.value());
                            assertEquals(8L, ((com.vellumhub.kafka.avro.book.BookChangedEvent)
                                    record.value()).getRevision(), "Offline revision 9 must not be published");
                            received++;
                        }
                        if (received > 0) break;
                    }
                    assertEquals(1, received, "Cold consumer must see exactly the accepted Kafka event");
                }
            } finally {
                registry.stop();
            }
        }
    }

    private static GenericContainer<?> registry(Network network) {
        return new GenericContainer<>(DockerImageName.parse("apicurio/apicurio-registry:3.1.7"))
                .withNetwork(network).withExposedPorts(8080)
                .withEnv("APICURIO_STORAGE_KIND", "sql")
                .withEnv("APICURIO_STORAGE_SQL_KIND", "postgresql")
                .withEnv("APICURIO_DATASOURCE_URL", "jdbc:postgresql://schema-db:5432/registrydb")
                .withEnv("APICURIO_DATASOURCE_USERNAME", "registry_user")
                .withEnv("APICURIO_DATASOURCE_PASSWORD", "integration-fixture-only")
                .waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)));
    }

    private static String url(GenericContainer<?> registry) {
        return "http://" + registry.getHost() + ":" + registry.getMappedPort(8080) + "/apis/ccompat/v7";
    }
    private static int register(String base, String subject, Schema schema) throws Exception {
        String request = JSON.writeValueAsString(Map.of("schemaType", "AVRO", "schema", schema.toString()));
        var response = api(base, "POST", "/subjects/" + subject + "/versions", request);
        assertEquals(200, response.statusCode());
        return JSON.readTree(response.body()).path("id").asInt(-1);
    }
    private static int versionId(String base, String subject, int version) throws Exception {
        var response = api(base, "GET", "/subjects/" + subject + "/versions/" + version, null);
        assertEquals(200, response.statusCode());
        return JSON.readTree(response.body()).path("id").asInt(-1);
    }
    private static HttpResponse<String> api(String base, String method, String path, String body)
            throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/vnd.schemaregistry.v1+json")
                .timeout(Duration.ofSeconds(15))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() :
                        HttpRequest.BodyPublishers.ofString(body)).build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }
    private static KafkaProducer<String, Object> producer(String bootstrap, String endpoint) {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        p.put("schema.registry.url", endpoint);
        p.put("auto.register.schemas", false);
        p.put("avro.remove.java.properties", true);
        p.put("value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
        p.put("max.retries", 0);
        p.put("request.timeout.ms", 1500);
        return new KafkaProducer<>(p);
    }
}
