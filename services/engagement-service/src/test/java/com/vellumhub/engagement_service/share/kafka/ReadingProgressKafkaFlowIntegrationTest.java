package com.vellumhub.engagement_service.share.kafka;

import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.readingprogress.CreateBookProgressEvent;
import com.vellumhub.kafka.contracts.readingprogress.UpdateBookProgressEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@DirtiesContext
@EmbeddedKafka(partitions = 1, bootstrapServersProperty = "spring.kafka.bootstrap-servers",
        topics = {KafkaTopics.CREATED_READING_PROGRESS, KafkaTopics.UPDATED_READING_PROGRESS})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
        "spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=com.vellumhub.kafka.contracts.readingprogress",
        "spring.kafka.consumer.properties.spring.json.type.mapping=create_book_progress_event:com.vellumhub.kafka.contracts.readingprogress.CreateBookProgressEvent,update_book_progress_event:com.vellumhub.kafka.contracts.readingprogress.UpdateBookProgressEvent",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.kafka.producer.properties.spring.json.type.mapping=create_book_progress_event:com.vellumhub.kafka.contracts.readingprogress.CreateBookProgressEvent,update_book_progress_event:com.vellumhub.kafka.contracts.readingprogress.UpdateBookProgressEvent",
        "management.health.kafka.enabled=false",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLXdpdGgtYXQtbGVhc3QtMjU2LWJpdHM="
})
class ReadingProgressKafkaFlowIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired KafkaTemplate<String, Object> kafka;
    @Autowired JdbcTemplate jdbc;

    @Test
    void preservesCatalogOccurrenceAndAcceptsLegacyMessages() throws Exception {
        UUID book = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID progress = UUID.randomUUID();
        jdbc.update("insert into book_snapshot (book_id) values (?)", book);
        UUID createdId = UUID.randomUUID();
        UUID updatedId = UUID.randomUUID();
        Instant occurred = Instant.parse("2026-09-21T10:00:00.123456Z");
        kafka.send(KafkaTopics.CREATED_READING_PROGRESS, user.toString(),
                new CreateBookProgressEvent(createdId, occurred, progress, user, book, "READING", 12))
                .get(10, TimeUnit.SECONDS);
        kafka.send(KafkaTopics.UPDATED_READING_PROGRESS, user.toString(),
                new UpdateBookProgressEvent(updatedId, occurred.plusSeconds(60), progress, user, book, "READING", 12, 90))
                .get(10, TimeUnit.SECONDS);
        kafka.send(KafkaTopics.UPDATED_READING_PROGRESS, user.toString(),
                new UpdateBookProgressEvent(progress, user, book, "READING", 90, 100))
                .get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("select count(*) from reading_session_entries where user_id = ?",
                    Integer.class, user)).isEqualTo(3);
            var row = jdbc.queryForMap("select * from reading_session_entries where event_id = ?", updatedId);
            assertThat(row.get("reading_session_id")).isEqualTo(progress);
            assertThat(row.get("user_id")).isEqualTo(user);
            assertThat(row.get("book_snapshot_book_id")).isEqualTo(book);
            assertThat(row.get("page_read")).isEqualTo(90);
            assertThat(row.get("type")).isEqualTo("READING");
            assertThat(jdbc.queryForObject("select timestamp from reading_session_entries where event_id = ?",
                    OffsetDateTime.class, updatedId).toInstant()).isEqualTo(occurred.plusSeconds(60));
            assertThat(jdbc.queryForObject("select timestamp from reading_session_entries where event_id = ?",
                    OffsetDateTime.class, createdId).toInstant()).isEqualTo(occurred);
            assertThat(jdbc.queryForMap("select event_id, timestamp from reading_session_entries where user_id = ? and page_read = 100", user))
                    .containsEntry("event_id", null).containsEntry("timestamp", null);
        });
    }
}
