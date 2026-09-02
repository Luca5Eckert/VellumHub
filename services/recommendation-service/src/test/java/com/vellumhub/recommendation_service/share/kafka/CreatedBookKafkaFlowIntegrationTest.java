package com.vellumhub.recommendation_service.share.kafka;

import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.CreateBookEvent;
import com.vellumhub.recommendation_service.module.book_feature.application.use_case.CreateBookFeatureUseCase;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.EmbeddingBookProvider;
import com.vellumhub.recommendation_service.support.DistributedIntegrationTestSupport;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("distributed")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class CreatedBookKafkaFlowIntegrationTest extends DistributedIntegrationTestSupport {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmbeddingBookProvider embeddingBookProvider;

    @MockitoSpyBean
    private CreateBookFeatureUseCase createBookFeatureUseCase;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from recommendation_genres");
        jdbcTemplate.update("delete from recommendations");
        jdbcTemplate.update("delete from book_features");

        float[] embedding = new float[384];
        Arrays.fill(embedding, 0.25f);
        when(embeddingBookProvider.of(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(embedding);
    }

    @Test
    void persistsCreatedBookProjectionFromRealKafkaIntoPgvectorPostgres() throws Exception {
        UUID bookId = UUID.randomUUID();
        CreateBookEvent event = eventFor(bookId);

        kafkaTemplate.send(KafkaTopics.CREATED_BOOK, bookId.toString(), event)
                .get(5, TimeUnit.SECONDS);

        await()
                .atMost(ASYNC_TIMEOUT)
                .pollInterval(ASYNC_POLL_INTERVAL)
                .untilAsserted(() -> {
                    assertThat(countByBookId("book_features", bookId)).isEqualTo(1L);
                    assertThat(countByBookId("recommendations", bookId)).isEqualTo(1L);
                    assertThat(jdbcTemplate.queryForObject(
                            "select vector_dims(embedding) from book_features where book_id = ?",
                            Integer.class,
                            bookId
                    )).isEqualTo(384);
                    assertThat(jdbcTemplate.queryForObject(
                            "select popularity_score from book_features where book_id = ?",
                            Double.class,
                            bookId
                    )).isEqualTo(1.0d);
                    assertThat(jdbcTemplate.queryForObject(
                            "select title from recommendations where book_id = ?",
                            String.class,
                            bookId
                    )).isEqualTo(event.title());
                    assertThat(jdbcTemplate.queryForObject(
                            "select author from recommendations where book_id = ?",
                            String.class,
                            bookId
                    )).isEqualTo(event.author());
                    assertThat(jdbcTemplate.queryForList(
                            "select genres from recommendation_genres where recommendation_book_id = ? order by genres",
                            String.class,
                            bookId
                    )).containsExactly("distributed-systems", "testing");
                });
    }

    @Test
    void retriesThreeTimesAndRoutesFailedCreatedBookToDlt() throws Exception {
        UUID bookId = UUID.randomUUID();
        CreateBookEvent event = eventFor(bookId);
        String dltTopic = KafkaTopics.CREATED_BOOK + KafkaTopics.DLT_SUFFIX;

        doThrow(new IllegalStateException("forced distributed-test failure"))
                .when(createBookFeatureUseCase)
                .execute(argThat(candidate -> candidate != null && bookId.equals(candidate.bookId())));

        try (KafkaConsumer<String, byte[]> dltConsumer = newDltConsumer()) {
            dltConsumer.subscribe(List.of(dltTopic));

            kafkaTemplate.send(KafkaTopics.CREATED_BOOK, bookId.toString(), event)
                    .get(5, TimeUnit.SECONDS);

            await()
                    .atMost(ASYNC_TIMEOUT)
                    .pollInterval(ASYNC_POLL_INTERVAL)
                    .untilAsserted(() -> verify(createBookFeatureUseCase, times(3))
                            .execute(argThat(candidate -> candidate != null && bookId.equals(candidate.bookId()))));

            ConsumerRecord<String, byte[]> dltRecord = awaitRecord(dltConsumer, dltTopic, bookId.toString());

            assertThat(dltRecord.topic()).isEqualTo(dltTopic);
            assertThat(dltRecord.key()).isEqualTo(bookId.toString());
            assertThat(dltRecord.value()).isNotEmpty();
            assertThat(originalTopicHeaders(dltRecord)).contains(KafkaTopics.CREATED_BOOK);
            assertThat(countByBookId("book_features", bookId)).isZero();
            assertThat(countByBookId("recommendations", bookId)).isZero();
        }
    }

    private CreateBookEvent eventFor(UUID bookId) {
        return new CreateBookEvent(
                bookId,
                "Distributed Systems in Practice",
                "A deterministic integration-test fixture",
                2026,
                "https://example.test/covers/distributed-systems.jpg",
                "VellumHub",
                List.of("distributed-systems", "testing")
        );
    }

    private KafkaConsumer<String, byte[]> newDltConsumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "created-book-dlt-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        return new KafkaConsumer<>(properties);
    }

    private ConsumerRecord<String, byte[]> awaitRecord(
            KafkaConsumer<String, byte[]> consumer,
            String expectedTopic,
            String expectedKey
    ) {
        AtomicReference<ConsumerRecord<String, byte[]>> match = new AtomicReference<>();

        await()
                .atMost(ASYNC_TIMEOUT)
                .pollInterval(ASYNC_POLL_INTERVAL)
                .until(() -> {
                    ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(250));
                    for (ConsumerRecord<String, byte[]> record : records) {
                        if (expectedTopic.equals(record.topic()) && expectedKey.equals(record.key())) {
                            match.set(record);
                            return true;
                        }
                    }
                    return false;
                });

        return match.get();
    }

    private List<String> originalTopicHeaders(ConsumerRecord<String, byte[]> record) {
        return StreamSupport.stream(
                        record.headers().headers(KafkaHeaders.ORIGINAL_TOPIC).spliterator(),
                        false
                )
                .map(header -> new String(header.value(), StandardCharsets.UTF_8))
                .toList();
    }

    private Long countByBookId(String table, UUID bookId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where book_id = ?",
                Long.class,
                bookId
        );
    }
}
