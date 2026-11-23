package com.vellumhub.recommendation_service.module.user_profile.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.kafka.avro.UserPreferenceAvroMapper;
import com.vellumhub.kafka.avro.userpreference.UserPreferenceChangedEvent;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.user.UserPreferenceChanged;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.EmbeddingUserProfileProvider;
import com.vellumhub.recommendation_service.support.DistributedIntegrationTestSupport;
import com.vellumhub.testing.distributed.avro.BookAvroFixture;
import com.vellumhub.testing.distributed.kafka.KafkaProbe;
import org.apache.avro.Schema;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("distributed")
@Tag("preference-avro-e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "app.kafka.json-listeners.enabled=false",
        "app.kafka.legacy-consumers.enabled=false",
        "app.user-preference.json.consumer-enabled=false",
        "app.user-preference.avro.consumer-enabled=true",
        "app.kafka.recovery.max-retries=0"
})
class PreferenceAvroRecommendationDistributedTest extends DistributedIntegrationTestSupport {
    private static final String TOPIC = KafkaTopics.USER_PREFERENCE_EVENTS_AVRO;
    private static final String GROUP = KafkaConsumerGroups.RECOMMENDATION_PREFERENCES_AVRO;
    private static int schemaId;

    @Container
    static final GenericContainer<?> REGISTRY = new GenericContainer<>(
            DockerImageName.parse("apicurio/apicurio-registry:3.1.7"))
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200));

    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmbeddingUserProfileProvider embeddings;

    @DynamicPropertySource
    static void registry(DynamicPropertyRegistry props) {
        props.add("app.user-preference.avro.registry-url", () -> BookAvroFixture.registryUrl(REGISTRY));
    }

    @BeforeAll
    static void registerPreferenceSchema() throws Exception {
        var json = new ObjectMapper();
        String type = "application/vnd.schemaregistry.v1+json";
        String registry = BookAvroFixture.registryUrl(REGISTRY);
        String subject = TOPIC + "-" + UserPreferenceChangedEvent.getClassSchema().getFullName();
        try (var stream = UserPreferenceChangedEvent.class.getResourceAsStream("/UserPreferenceChangedEvent.avsc");
             var http = HttpClient.newHttpClient()) {
            assertThat(stream).isNotNull();
            // Match the compact schema representation used by the Registry bootstrap and serializer lookup.
            String schema = new Schema.Parser().parse(stream).toString();
            var config = HttpRequest.newBuilder(URI.create(registry + "/config/" + subject))
                    .header("Content-Type", type).timeout(Duration.ofSeconds(10))
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"compatibility\":\"BACKWARD_TRANSITIVE\"}")).build();
            assertThat(http.send(config, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
            var registration = HttpRequest.newBuilder(URI.create(registry + "/subjects/" + subject + "/versions"))
                    .header("Content-Type", type).timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                            Map.of("schemaType", "AVRO", "schema", schema)))).build();
            var response = http.send(registration, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            schemaId = json.readTree(response.body()).path("id").asInt(-1);
            assertThat(schemaId).isPositive();
        }
    }

    @Test
    void commitsTheProfileOnceAndQuarantinesAGapWithOriginalAvroBytes() throws Exception {
        var user = UUID.randomUUID();
        var genres = List.of("Fantasy");
        float[] vector = new float[384];
        vector[0] = 1;
        when(embeddings.of(genres, null)).thenReturn(vector);

        String broker = KAFKA.getBootstrapServers();
        String dltTopic = TOPIC + ".recommendation-service-dlt";
        var first = new UserPreferenceChanged(UUID.randomUUID(), Instant.EPOCH, user, 0, genres, null);
        var gap = new UserPreferenceChanged(UUID.randomUUID(), Instant.EPOCH, user, 2, genres, null);

        try (var producer = BookAvroFixture.producer(broker, BookAvroFixture.registryUrl(REGISTRY));
             var dlt = KafkaProbe.subscribe(broker, dltTopic, "preference-avro-dlt-fixture")) {
            send(producer, first);
            send(producer, first);
            send(producer, gap);

            var dead = dlt.awaitRecord(dltTopic, user.toString(), Duration.ofSeconds(25), Duration.ofMillis(200));
            assertThat(dead.value()).hasSizeGreaterThan(5);
            assertThat(dead.value()[0]).isEqualTo((byte) 0);
            assertThat(ByteBuffer.wrap(dead.value(), 1, 4).getInt()).isEqualTo(schemaId);
            assertThat(KafkaProbe.utf8HeaderValues(dead, "x-vellum-source-topic")).containsExactly(TOPIC);
            assertThat(KafkaProbe.utf8HeaderValues(dead, "x-vellum-consumer-group")).containsExactly(GROUP);
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM kafka_consumer_quarantine
                        WHERE consumer_group=? AND source_topic=? AND status='BLOCKED'
                        """, Integer.class, GROUP, TOPIC)).isEqualTo(1);
                assertThat(jdbc.queryForObject(
                        "SELECT last_revision FROM preference_event_offsets WHERE user_id=?", Long.class, user)).isZero();
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM processed_preference_events WHERE user_id=?", Integer.class, user)).isEqualTo(1);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM user_profiles WHERE user_id=? AND profile_vector IS NOT NULL",
                        Integer.class, user)).isEqualTo(1);
            });
            verify(embeddings, times(1)).of(genres, null);
        }
    }

    private static void send(KafkaProducer<String, Object> producer, UserPreferenceChanged event) throws Exception {
        producer.send(new ProducerRecord<>(TOPIC, event.userId().toString(),
                UserPreferenceAvroMapper.toAvro(event))).get(15, TimeUnit.SECONDS);
    }
}
