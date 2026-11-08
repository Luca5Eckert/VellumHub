package com.vellumhub.engagement_service.module.book_event.infrastructure.kafka;

import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import com.vellumhub.testing.distributed.avro.BookAvroFixture;
import com.vellumhub.testing.distributed.container.KafkaIntegrationTestSupport;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Real Engagement listener, SQL snapshot+checkpoint, Avro framing and blocked original offset. */
@Tag("distributed")
@Tag("book-avro-e2e")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"app.book.avro.consumer-enabled=true",
                "app.kafka.recovery.max-retries=0",
                "app.outbox.enabled=false",
                "spring.kafka.listener.concurrency=1"})
class BookAvroEngagementDistributedTest extends KafkaIntegrationTestSupport {
    @Container static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static final GenericContainer<?> REGISTRY =
            new GenericContainer<>(DockerImageName.parse("apicurio/apicurio-registry:3.1.7"))
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200));

    @DynamicPropertySource
    static void dependencies(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        props.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        props.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        props.add("spring.datasource.username", POSTGRES::getUsername);
        props.add("spring.datasource.password", POSTGRES::getPassword);
        props.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        props.add("spring.flyway.enabled", () -> "true");
        props.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        props.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        props.add("spring.kafka.admin.fail-fast", () -> "true");
        props.add("jwt.public-key-location", () -> "classpath:security/test-public.pem");
        props.add("management.health.kafka.enabled", () -> "false");
        props.add("app.book.avro.registry-url", () -> BookAvroFixture.registryUrl(REGISTRY));
    }

    @BeforeAll static void register() throws Exception {
        BookAvroFixture.register(BookAvroFixture.registryUrl(REGISTRY));
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void appliesSnapshotTombstoneAndDeduplicatesAcrossAvroBeforeBlockingRevisionGap() throws Exception {
        var book = UUID.randomUUID();
        String topic = KafkaTopics.BOOK_EVENTS_AVRO;
        String dltTopic = topic + ".engagement-service-dlt";
        String registry = BookAvroFixture.registryUrl(REGISTRY);
        int schemaId = BookAvroFixture.register(registry);
        try (var producer = BookAvroFixture.producer(KAFKA.getBootstrapServers(), registry);
             var dlt = KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                     dltTopic, "engagement-book-avro-dlt")) {
            for (int revision = 0; revision <= 1; revision++) {
                BookChangedEvent.Kind kind = revision == 0
                        ? BookChangedEvent.Kind.CREATED : BookChangedEvent.Kind.UPDATED;
                var event = BookAvroFixture.event(book, revision, kind);
                producer.send(new org.apache.kafka.clients.producer.ProducerRecord<>(
                        topic, book.toString(), BookAvroFixture.encoded(event)))
                        .get(15, TimeUnit.SECONDS);
                final long expected = revision;
                await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
                    assertThat(jdbc.queryForObject("SELECT last_revision FROM book_event_offsets WHERE book_id=?",
                            Long.class, book)).isEqualTo(expected);
                    assertThat(jdbc.queryForObject("SELECT active FROM book_snapshot WHERE book_id=?",
                            Boolean.class, book)).isTrue();
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
            var deleted = BookAvroFixture.event(book, 2, BookChangedEvent.Kind.DELETED);
            producer.send(new org.apache.kafka.clients.producer.ProducerRecord<>(
                    topic, book.toString(), BookAvroFixture.encoded(deleted)))
                    .get(15, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
                assertThat(jdbc.queryForObject("SELECT last_revision FROM book_event_offsets WHERE book_id=?",
                        Long.class, book)).isEqualTo(2L);
                assertThat(jdbc.queryForObject("SELECT active FROM book_snapshot WHERE book_id=?",
                        Boolean.class, book)).isFalse();
            });
            var gap = BookAvroFixture.event(book, 4, BookChangedEvent.Kind.UPDATED);
            producer.send(new org.apache.kafka.clients.producer.ProducerRecord<>(
                    topic, book.toString(), BookAvroFixture.encoded(gap)))
                    .get(15, TimeUnit.SECONDS);
            var dead = dlt.awaitRecord(dltTopic, book.toString(),
                    Duration.ofSeconds(25), Duration.ofMillis(200));
            assertThat(dead.value()).hasSizeGreaterThan(5);
            assertThat(dead.value()[0]).isEqualTo((byte) 0);
            assertThat(ByteBuffer.wrap(dead.value(), 1, 4).getInt()).isEqualTo(schemaId);
            assertThat(KafkaProbe.utf8HeaderValues(dead, "x-vellum-source-topic")).containsExactly(topic);
            assertThat(KafkaProbe.utf8HeaderValues(dead, "x-vellum-consumer-group"))
                    .containsExactly(KafkaConsumerGroups.ENGAGEMENT_BOOK_AVRO);
            assertThat(KafkaProbe.utf8HeaderValues(dead, "x-vellum-source-offset")).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT last_revision FROM book_event_offsets WHERE book_id=?",
                    Long.class, book)).isEqualTo(2L);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM kafka_consumer_quarantine
                    WHERE consumer_group=? AND source_topic=? AND status='BLOCKED'
                    """, Integer.class, KafkaConsumerGroups.ENGAGEMENT_BOOK_AVRO, topic)).isPositive();
        }
    }
}
