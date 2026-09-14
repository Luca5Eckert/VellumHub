package com.vellumhub.engagement_service.module.rating.domain.producer;

import com.vellumhub.engagement_service.module.rating.domain.model.Rating;
import com.vellumhub.engagement_service.module.rating.domain.model.RatingUpdateResult;
import com.vellumhub.engagement_service.module.rating.domain.port.EventProducer;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.UpdatedRatingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UpdatedRatingEventProducerTest {

    @Mock
    private EventProducer<String, UpdatedRatingEvent> eventProducer;

    @InjectMocks
    private UpdatedRatingEventProducer updatedRatingEventProducer;

    @Test
    @DisplayName("Should publish the real rating transition on updated-rating")
    void shouldPublishUpdatedRatingEvent() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        Rating rating = Rating.builder()
                .id(77L)
                .userId(userId)
                .bookId(bookId)
                .stars(4)
                .review("Much better")
                .build();
        RatingUpdateResult updateResult = new RatingUpdateResult(rating, 2, true);
        ArgumentCaptor<UpdatedRatingEvent> eventCaptor = ArgumentCaptor.forClass(UpdatedRatingEvent.class);

        updatedRatingEventProducer.produce(updateResult);

        verify(eventProducer).send(
                eq(KafkaTopics.UPDATED_RATING),
                eq(userId.toString()),
                eventCaptor.capture()
        );

        UpdatedRatingEvent event = eventCaptor.getValue();
        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.ratingId()).isEqualTo(77L);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.bookId()).isEqualTo(bookId);
        assertThat(event.oldStars()).isEqualTo(2);
        assertThat(event.newStars()).isEqualTo(4);
        assertThat(event.reviewChanged()).isTrue();
    }

    @Test
    @DisplayName("Should publish review-only updates with unchanged stars")
    void shouldPublishReviewOnlyUpdate() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        Rating rating = Rating.builder()
                .id(78L)
                .userId(userId)
                .bookId(bookId)
                .stars(4)
                .review("Updated review")
                .build();
        RatingUpdateResult updateResult = new RatingUpdateResult(rating, 4, true);
        ArgumentCaptor<UpdatedRatingEvent> eventCaptor = ArgumentCaptor.forClass(UpdatedRatingEvent.class);

        updatedRatingEventProducer.produce(updateResult);

        verify(eventProducer).send(
                eq(KafkaTopics.UPDATED_RATING),
                eq(userId.toString()),
                eventCaptor.capture()
        );

        assertThat(eventCaptor.getValue().oldStars()).isEqualTo(4);
        assertThat(eventCaptor.getValue().newStars()).isEqualTo(4);
        assertThat(eventCaptor.getValue().reviewChanged()).isTrue();
    }
}
