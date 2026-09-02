package com.vellumhub.recommendation_service.share.kafka;

import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.CreateBookEvent;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.EmbeddingBookProvider;
import com.vellumhub.recommendation_service.support.DistributedIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class CreatedBookKafkaFlowIntegrationTest extends DistributedIntegrationTestSupport {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmbeddingBookProvider embeddingBookProvider;

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
        CreateBookEvent event = new CreateBookEvent(
                bookId,
                "Distributed Systems in Practice",
                "A deterministic integration-test fixture",
                2026,
                "https://example.test/covers/distributed-systems.jpg",
                "VellumHub",
                List.of("distributed-systems", "testing")
        );

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

    private Long countByBookId(String table, UUID bookId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where book_id = ?",
                Long.class,
                bookId
        );
    }
}
