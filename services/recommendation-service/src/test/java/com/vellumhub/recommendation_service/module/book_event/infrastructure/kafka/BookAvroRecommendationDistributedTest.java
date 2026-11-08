package com.vellumhub.recommendation_service.module.book_event.infrastructure.kafka;

import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.EmbeddingBookProvider;
import com.vellumhub.recommendation_service.support.DistributedIntegrationTestSupport;
import com.vellumhub.testing.distributed.avro.BookAvroFixture;
import com.vellumhub.testing.distributed.kafka.KafkaProbe;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Kafka Avro bytes -> real Recommendation listener and PostgreSQL projections/checkpoints. */
@Tag("distributed")
@Tag("book-avro-e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"app.kafka.legacy-consumers.enabled=false",
                "app.book.avro.consumer-enabled=true", "app.kafka.recovery.max-retries=0",
                "spring.kafka.listener.concurrency=1"})
class BookAvroRecommendationDistributedTest extends DistributedIntegrationTestSupport {
    @Container static final GenericContainer<?> REGISTRY =
            new GenericContainer<>(DockerImageName.parse("apicurio/apicurio-registry:3.1.7"))
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200));

    @DynamicPropertySource
    static void registry(DynamicPropertyRegistry props) {
        props.add("app.book.avro.registry-url", () -> BookAvroFixture.registryUrl(REGISTRY));
    }

    @BeforeAll static void register() throws Exception {
        BookAvroFixture.register(BookAvroFixture.registryUrl(REGISTRY));
    }

    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmbeddingBookProvider embeddings;

    @Test
    void createsUpdatesDeduplicatesDeletesAndQuarantinesGapWithoutAdvancingCheckpoint() throws Exception {
        float[] vector = new float[384];
        java.util.Arrays.fill(vector, 0.25f);
        when(embeddings.of(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(vector);
        var book = UUID.randomUUID();
        String broker = KAFKA.getBootstrapServers();
        String registry = BookAvroFixture.registryUrl(REGISTRY);
        String topic = KafkaTopics.BOOK_EVENTS_AVRO;
        String dltTopic = topic + ".recommendation-service-dlt";
        int schemaId = BookAvroFixture.register(registry);

        try (var producer = BookAvroFixture.producer(broker, registry);
             var dlt = KafkaProbe.subscribe(broker, dltTopic, "recommendation-book-avro-dlt")) {
            for (int revision = 0; revision <= 1; revision++) {
                var kind = revision == 0 ? BookChangedEvent.Kind.CREATED : BookChangedEvent.Kind.UPDATED;
                var event = BookAvroFixture.event(book, revision, kind);
                producer.send(new org.apache.kafka.clients.producer.ProducerRecord<>(
                        topic, book.toString(), BookAvroFixture.encoded(event)))
                        .get(15, TimeUnit.SECONDS);
                final long expected = revision;
                await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
                    assertThat(jdbc.queryForObject("SELECT last_revision FROM book_event_offsets WHERE book_id=?",
                            Long.class, book)).isEqualTo(expected);
                    assertThat(jdbc.queryForObject("SELECT title FROM recommendations WHERE book_id=?",
                            String.class, book)).isEqualTo("Book-" + expected);
                });
                if (revision == 1) {
                    producer.send(new org.apache.kafka.clients.producer.ProducerRecord<>(
                            topic, book.toString(), BookAvroFixture.encoded(event)))
                            .get(15, TimeUnit.SECONDS);
                    await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2))
                            .untilAsserted(() -> assertThat(jdbc.queryForObject(
                                    "SELECT last_revision FROM book_event_offsets WHERE book_id=?",
                                    Long.class, book)).isEqualTo(1L));
                }
            }
            var deleted = BookAvroFixture.event(book, 2L, BookChangedEvent.Kind.DELETED);
            producer.send(new org.apache.kafka.clients.producer.ProducerRecord<>(
                    topic, book.toString(), BookAvroFixture.encoded(deleted))).get(15, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
                assertThat(jdbc.queryForObject("SELECT last_revision FROM book_event_offsets WHERE book_id=?",
                        Long.class, book)).isEqualTo(2L);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendations WHERE book_id=?",
                        Integer.class, book)).isZero();
            });
            // Source gap 4 (missing revision 3) must remain blocked at its original offset.
            var gap = BookAvroFixture.event(book, 4, BookChangedEvent.Kind.UPDATED);
            producer.send(new org.apache.kafka.clients.producer.ProducerRecord<>(
                    topic, book.toString(), BookAvroFixture.encoded(gap))).get(15, TimeUnit.SECONDS);
            var dead = dlt.awaitRecord(dltTopic, book.toString(),
                    Duration.ofSeconds(25), Duration.ofMillis(200));
            assertThat(dead.value()).hasSizeGreaterThan(5);
            assertThat(dead.value()[0]).isEqualTo((byte) 0);
            assertThat(ByteBuffer.wrap(dead.value(), 1, 4).getInt()).isEqualTo(schemaId);
            assertThat(KafkaProbe.utf8HeaderValues(dead, "x-vellum-source-topic")).containsExactly(topic);
            assertThat(KafkaProbe.utf8HeaderValues(dead, "x-vellum-consumer-group"))
                    .containsExactly(KafkaConsumerGroups.RECOMMENDATION_BOOK_AVRO);
            assertThat(KafkaProbe.utf8HeaderValues(dead, "x-vellum-source-offset")).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT last_revision FROM book_event_offsets WHERE book_id=?",
                    Long.class, book)).isEqualTo(2L);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM kafka_consumer_quarantine
                    WHERE consumer_group=? AND source_topic=? AND status='BLOCKED'
                    """, Integer.class, KafkaConsumerGroups.RECOMMENDATION_BOOK_AVRO, topic)).isPositive();
        }
    }
}
