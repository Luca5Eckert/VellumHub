package com.vellumhub.recommendation_service.share.kafka;

import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.readingprogress.CreateBookProgressEvent;
import com.vellumhub.kafka.contracts.readingprogress.UpdateBookProgressEvent;
import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.UserProfile;
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
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("distributed")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ReadingProgressKafkaFlowIntegrationTest extends DistributedIntegrationTestSupport {

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
    void consumesCatalogProgressDirectlyIntoUserProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        float[] embedding = new float[384];
        embedding[0] = 1.0f;
        bookFeatureRepository.save(BookFeature.create(bookId, embedding, 1.0));

        UUID progressId = UUID.randomUUID();
        CreateBookProgressEvent created = new CreateBookProgressEvent(
                UUID.randomUUID(), Instant.now(), progressId, userId, bookId, "READING", 80);

        kafkaTemplate.send(KafkaTopics.CREATED_READING_PROGRESS, userId.toString(), created)
                .get(5, TimeUnit.SECONDS);

        awaitProfile(userId, profile ->
                assertThat(profile.getTotalEngagementScore()).isEqualTo(2.0)
        );

        UpdateBookProgressEvent updated = new UpdateBookProgressEvent(
                UUID.randomUUID(), Instant.now(), progressId, userId, bookId, "COMPLETED", 80, 160);

        kafkaTemplate.send(KafkaTopics.UPDATED_READING_PROGRESS, userId.toString(), updated)
                .get(5, TimeUnit.SECONDS);

        awaitProfile(userId, profile -> {
            assertThat(profile.getTotalEngagementScore()).isEqualTo(5.0);
            assertThat(profile.getInteractedBookIds()).containsExactly(bookId);
        });
    }

    @Test
    void missingFeaturesAreSkippedAndThePartitionContinues() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID absentBook = UUID.randomUUID();
        UUID knownBook = UUID.randomUUID();
        float[] embedding = new float[384];
        embedding[0] = 1.0f;
        bookFeatureRepository.save(BookFeature.create(knownBook, embedding, 1.0));
        // Explicit same partition makes the valid event a consumption barrier for the missing one.
        kafkaTemplate.send(KafkaTopics.CREATED_READING_PROGRESS, 0, userId.toString(),
                new CreateBookProgressEvent(UUID.randomUUID(), Instant.now(), UUID.randomUUID(),
                        userId, absentBook, "READING", 80)).get(5, TimeUnit.SECONDS);
        kafkaTemplate.send(KafkaTopics.CREATED_READING_PROGRESS, 0, userId.toString(),
                new CreateBookProgressEvent(UUID.randomUUID(), Instant.now(), UUID.randomUUID(),
                        userId, knownBook, "READING", 0)).get(5, TimeUnit.SECONDS);
        awaitProfile(userId, profile -> {
            assertThat(profile.getTotalEngagementScore()).isEqualTo(1.0);
            assertThat(profile.getInteractedBookIds()).containsExactly(knownBook);
        });
    }

    private void awaitProfile(UUID userId, Consumer<UserProfile> assertion) {
        await()
                .atMost(ASYNC_TIMEOUT)
                .pollInterval(ASYNC_POLL_INTERVAL)
                .untilAsserted(() -> {
                    var profile = userProfileRepository.findById(userId);
                    assertThat(profile).isPresent();
                    assertion.accept(profile.get());
                });
    }
}
