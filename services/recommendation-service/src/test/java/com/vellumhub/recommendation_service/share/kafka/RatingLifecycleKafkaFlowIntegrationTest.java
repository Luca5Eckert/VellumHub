package com.vellumhub.recommendation_service.share.kafka;

import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.CreatedRatingEvent;
import com.vellumhub.kafka.contracts.engagement.UpdatedRatingEvent;
import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
import com.vellumhub.recommendation_service.support.DistributedIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("distributed")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class RatingLifecycleKafkaFlowIntegrationTest extends DistributedIntegrationTestSupport {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private BookFeatureRepository bookFeatureRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from user_profiles");
        jdbcTemplate.update("delete from book_features");
    }

    @Test
    void consumesCreatedAndUpdatedRatingLifecycleIntoUserProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        float[] embedding = new float[384];
        embedding[0] = 1.0f;
        bookFeatureRepository.save(BookFeature.create(bookId, embedding, 1.0));

        CreatedRatingEvent created = new CreatedRatingEvent(
                UUID.randomUUID(),
                Instant.now(),
                1L,
                userId,
                bookId,
                null,
                2,
                false
        );

        kafkaTemplate.send(KafkaTopics.CREATED_RATING, userId.toString(), created)
                .get(5, TimeUnit.SECONDS);

        await()
                .atMost(ASYNC_TIMEOUT)
                .pollInterval(ASYNC_POLL_INTERVAL)
                .untilAsserted(() -> {
                    var profile = userProfileRepository.findById(userId).orElseThrow();
                    assertThat(profile.getTotalEngagementScore()).isEqualTo(-5.0);
                });

        UpdatedRatingEvent updated = new UpdatedRatingEvent(
                UUID.randomUUID(),
                Instant.now(),
                1L,
                userId,
                bookId,
                2,
                4,
                false
        );

        kafkaTemplate.send(KafkaTopics.UPDATED_RATING, userId.toString(), updated)
                .get(5, TimeUnit.SECONDS);

        await()
                .atMost(ASYNC_TIMEOUT)
                .pollInterval(ASYNC_POLL_INTERVAL)
                .untilAsserted(() -> {
                    var profile = userProfileRepository.findById(userId).orElseThrow();
                    assertThat(profile.getTotalEngagementScore()).isEqualTo(5.0);
                    assertThat(profile.getInteractedBookIds()).containsExactly(bookId);
                });
    }
}
