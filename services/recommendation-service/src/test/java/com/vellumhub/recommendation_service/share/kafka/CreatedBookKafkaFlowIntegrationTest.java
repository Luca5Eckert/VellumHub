package com.vellumhub.recommendation_service.share.kafka;

import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.CreateBookEvent;
import com.vellumhub.recommendation_service.module.book_feature.application.use_case.CreateBookFeatureUseCase;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.EmbeddingBookProvider;
import com.vellumhub.recommendation_service.support.DistributedIntegrationTestSupport;
import com.vellumhub.testing.distributed.fixture.BookEventFixtures;
import com.vellumhub.testing.distributed.kafka.KafkaProbe;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
    void retriesThreeTimesAndRoutesFailedCreatedBookToDlt() throws Exception {
        UUID bookId = UUID.randomUUID();
        CreateBookEvent event = BookEventFixtures.createdBook(bookId);
        String dltTopic = KafkaTopics.CREATED_BOOK + KafkaTopics.DLT_SUFFIX;

        doThrow(new IllegalStateException("forced distributed-test failure"))
                .when(createBookFeatureUseCase)
                .execute(argThat(candidate -> candidate != null && bookId.equals(candidate.bookId())));

        try (KafkaProbe dltProbe = KafkaProbe.subscribe(
                KAFKA.getBootstrapServers(),
                dltTopic,
                "created-book-dlt-test"
        )) {
            kafkaTemplate.send(KafkaTopics.CREATED_BOOK, bookId.toString(), event)
                    .get(5, TimeUnit.SECONDS);

            await()
                    .atMost(ASYNC_TIMEOUT)
                    .pollInterval(ASYNC_POLL_INTERVAL)
                    .untilAsserted(() -> verify(createBookFeatureUseCase, times(3))
                            .execute(argThat(candidate -> candidate != null && bookId.equals(candidate.bookId()))));

            ConsumerRecord<String, byte[]> dltRecord = dltProbe.awaitRecord(
                    dltTopic,
                    bookId.toString(),
                    ASYNC_TIMEOUT,
                    ASYNC_POLL_INTERVAL
            );

            assertThat(dltRecord.topic()).isEqualTo(dltTopic);
            assertThat(dltRecord.key()).isEqualTo(bookId.toString());
            assertThat(dltRecord.value()).isNotEmpty();
            assertThat(KafkaProbe.utf8HeaderValues(dltRecord, KafkaHeaders.ORIGINAL_TOPIC))
                    .contains(KafkaTopics.CREATED_BOOK);
            assertThat(countByBookId("book_features", bookId)).isZero();
            assertThat(countByBookId("recommendations", bookId)).isZero();
        }
    }

    private Long countByBookId(String table, UUID bookId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where book_id = ?",
                Long.class,
                bookId
        );
    }
}
