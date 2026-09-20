package com.vellumhub.recommendation_service.share.kafka;

import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.ReactionChangedEvent;
import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.ReactionChangedUseCase;
import com.vellumhub.recommendation_service.module.user_profile.domain.interaction.reaction.ReactionBookInteraction;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.UserProfile;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
import com.vellumhub.recommendation_service.module.user_profile.presentation.consumer.UserReactionConsumerEvent;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ReactionLifecycleSerializationTest {

    @Test
    void enrichedPayloadRoundTripsThroughTheKafkaTypeAlias() {
        var event = new ReactionChangedEvent(UUID.randomUUID(), Instant.parse("2026-09-19T12:00:00.123456Z"),
                42L, UUID.randomUUID(), UUID.randomUUID(), "POSITIVE", "VERY_POSITIVE");
        var headers = new RecordHeaders();
        String mapping = "reaction_changed_event:" + ReactionChangedEvent.class.getName();
        try (var serializer = new JsonSerializer<ReactionChangedEvent>();
             var deserializer = new JsonDeserializer<ReactionChangedEvent>()) {
            serializer.configure(Map.of(JsonSerializer.TYPE_MAPPINGS, mapping), false);
            deserializer.configure(Map.of(JsonDeserializer.TYPE_MAPPINGS, mapping,
                    JsonDeserializer.TRUSTED_PACKAGES, "com.vellumhub.kafka.contracts.engagement"), false);
            byte[] payload = serializer.serialize(KafkaTopics.USER_REACTION_CHANGED, headers, event);
            assertThat(new String(headers.lastHeader("__TypeId__").value(), StandardCharsets.UTF_8))
                    .isEqualTo("reaction_changed_event");
            assertThat(deserializer.deserialize(KafkaTopics.USER_REACTION_CHANGED, headers, payload))
                    .isEqualTo(event);
        }
    }

    @Test
    void legacyJsonUsesTheFallbackWithoutInventingAuditMetadata() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        String json = """
                {"userId":"%s","bookId":"%s","typeReaction":"POSITIVE"}
                """.formatted(userId, bookId);
        try (var deserializer = new JsonDeserializer<>(ReactionChangedEvent.class, false)) {
            var event = deserializer.deserialize(KafkaTopics.USER_REACTION_CHANGED,
                    json.getBytes(StandardCharsets.UTF_8));
            assertThat(event.userId()).isEqualTo(userId);
            assertThat(event.bookId()).isEqualTo(bookId);
            assertThat(event.eventId()).isNull();
            assertThat(event.occurredAt()).isNull();
            assertThat(event.reactionId()).isNull();
            assertThat(event.oldTypeReaction()).isNull();
            assertThat(event.newTypeReaction()).isNull();
            assertThat(event.resultingTypeReaction()).isEqualTo("POSITIVE");
        }
    }

    @Test
    void serializedTransitionsReachTheProfileAndMissingFeaturesRemainRetryable() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        var books = mock(BookFeatureRepository.class);
        var profiles = mock(UserProfileRepository.class);
        var profile = new UserProfile(userId);
        float[] embedding = new float[384];
        embedding[0] = 1.0f;
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));
        when(books.findById(bookId)).thenReturn(Optional.empty(),
                Optional.of(BookFeature.create(bookId, embedding, 1.0)));
        var registry = new SimpleMeterRegistry();
        try (var serializer = new JsonSerializer<ReactionChangedEvent>();
             var deserializer = new JsonDeserializer<>(ReactionChangedEvent.class, false)) {
            var consumer = new UserReactionConsumerEvent(
                    new ReactionChangedUseCase(profiles, books, new ReactionBookInteraction()),
                    new VellumHubMetrics(registry));
            var creation = new ReactionChangedEvent(UUID.randomUUID(), Instant.now(), 1L,
                    userId, bookId, null, "POSITIVE");
            assertThatThrownBy(() -> consumer.consume(creation)).isInstanceOf(IllegalStateException.class);
            verify(profiles, never()).save(any());
            consumer.consume(deserializer.deserialize(KafkaTopics.USER_REACTION_CHANGED,
                    serializer.serialize(KafkaTopics.USER_REACTION_CHANGED, creation)));
            assertThat(profile.getTotalEngagementScore()).isEqualTo(1.5);
            float[] vectorBefore = profile.getProfileVector().clone();
            var unchanged = new ReactionChangedEvent(UUID.randomUUID(), Instant.now(), 1L,
                    userId, bookId, "POSITIVE", "POSITIVE");
            consumer.consume(deserializer.deserialize(KafkaTopics.USER_REACTION_CHANGED,
                    serializer.serialize(KafkaTopics.USER_REACTION_CHANGED, unchanged)));
            assertThat(profile.getTotalEngagementScore()).isEqualTo(1.5);
            assertThat(profile.getProfileVector()).containsExactly(vectorBefore);
            var negative = new ReactionChangedEvent(UUID.randomUUID(), Instant.now(), 1L,
                    userId, bookId, "POSITIVE", "NEGATIVE");
            consumer.consume(deserializer.deserialize(KafkaTopics.USER_REACTION_CHANGED,
                    serializer.serialize(KafkaTopics.USER_REACTION_CHANGED, negative)));
            assertThat(profile.getTotalEngagementScore()).isEqualTo(-0.5);
            assertThat(profile.getInteractedBookIds()).containsExactly(bookId);
            verify(profiles, times(3)).save(profile);
        } finally {
            registry.close();
        }
    }
}
